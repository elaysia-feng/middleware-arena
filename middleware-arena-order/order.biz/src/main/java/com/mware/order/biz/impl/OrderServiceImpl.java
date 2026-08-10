package com.mware.order.biz.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.github.benmanes.caffeine.cache.Cache;
import com.mware.common.web.ApiException;
import com.mware.common.web.ApiResponse;
import com.mware.common.web.ErrorCode;
import com.mware.common.web.UserContext;
import com.mware.order.biz.OrderService;
import com.mware.order.domain.Order;
import com.mware.order.domain.OrderStatus;
import com.mware.order.dto.request.CreateOrderRequest;
import com.mware.order.dto.response.OrderResponse;
import com.mware.order.feign.AccountClient;
import com.mware.order.feign.ProductClient;
import com.mware.order.feign.StorageClient;
import com.mware.order.mapper.OrderMapper;
import com.mware.product.domain.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.apache.seata.spring.annotation.GlobalTransactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 订单业务实现。
 * <p>
 * createOrder / getOrder 已可用（幂等、缓存降级、IDOR 校验均已实现），剩余 TODO：
 * <ol>
 * <li>Seata 分布式事务收尾：建 undo_log 表（见 sql/init.sql）+ application.yml 开启 Seata AT；
 * storage / account 需作为参与方加入同一全局事务（参与方 DDL 已含 undo_log）。</li>
 * <li>RabbitMQ 异步下单通知：下单成功后投递订单事件（order.created）至 MQ，
 * 通知服务 / 邮件 / 站内信消费；注意消息投递与本地事务的一致性。</li>
 * <li>订单超时自动取消：定时任务（如 Spring {@code @Scheduled} / XXL-Job）扫描 CREATE 超 N 分钟
 * 未 PAID 的订单置 CANCEL，并回补库存（storage 加回）/ 余额（account 加回）；注意回补需再走一次分布式事务。</li>
 * <li>幂等兜底：request_id 已从 Order 表移除，现仅靠 Redis SETNX 幂等 key 防重；
 * DB 唯一索引兜底留作未来实验（见 sql/init.sql 注释），若要启用需回加 request_id 列。</li>
 * <li>状态流转：支付回调 / 关单（PAID / CANCEL 变更），并同步订单详情缓存。</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {
    private final String orderKeyPrefix = "create:order:";
    private final OrderMapper orderMapper;
    private final StorageClient storageClient;
    private final AccountClient accountClient;
    private final ProductClient productClient;
    private final RedisTemplate redisTemplate;
    private final Cache<Long, Order> orderCache;

    @Override
    @GlobalTransactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        // 0. requestId 兜底
        String requestId = request.getRequestId();
        if (!StringUtils.hasText(requestId)) {
            requestId = UUID.randomUUID().toString().replace("-", "");
        }
        // 身份一律从 UserContext 取，不信任请求体（CreateOrderRequest 已不含 userId）
        Long uid = UserContext.getUserId();
        if (uid == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }

        // 1. 幂等请求 然后 再组装 Order（userId/productId/quantity/amount/status=CREATED）
        Boolean first = redisTemplate.opsForValue().setIfAbsent(orderKeyPrefix + requestId, uid, Duration.ofMinutes(5));
        if (!first) {
            // 已提交过：Redis SETNX 幂等 key 已存在，同一 requestId 直接拒绝。
            // 注：request_id 已从 Order 表移除（DB 兜底查询是未来实验，见 sql/init.sql 注释），
            // 现在只靠 Redis key 防重，TTL 5 分钟。
            // TODO 完善逻辑：
            // 1. 返回"处理中"状态码（如 202 / 自定义码）让前端稍后重查，而非直接 PARAM_INVALID
            // 2. 可提供轮询接口（按 requestId 查订单）等待落库完成
            // 3. 注意幂等 key TTL（5 分钟）与订单事务提交的竞态：TTL 过短 → key 先失效、订单后落库 → 重试会重复下单
            throw new ApiException(ErrorCode.PARAM_INVALID);
        }
        // 如果下单数量为0 则为系统错误
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new ApiException(ErrorCode.PARAM_INVALID);
        }

        try {
            Product product = productClient.getProduct(request.getProductId()).getData();
            // 商品不存在就直接报错
            if (product == null) {
                throw new ApiException(ErrorCode.PRODUCT_NOT_FOUND);
            }

            // 金额统一 Long（单位：分），避免浮点误差：单价分 × 数量
            Long amount = product.getPrice() * request.getQuantity();

            Order order = Order.builder()
                    .userId(uid)
                    .productId(request.getProductId())
                    .quantity(request.getQuantity())
                    .orderNo(generateOrderNo())
                    .unitPrice(product.getPrice())
                    .amount(amount)
                    .status(OrderStatus.CREATE.getStatus())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            // 2. 插入订单
            orderMapper.insert(order);

            // 3. storageClient.deductStock(productId, quantity) 扣库存，库存不足抛
            // ApiException(STOCK_NOT_ENOUGH)
            storageClient.deductStock(request.getProductId(), request.getQuantity());

            // 4. accountClient.deductBalance(userId, amount) 扣余额，余额不足抛
            // ApiException(BALANCE_NOT_ENOUGH)
            accountClient.deductBalance(uid, amount);

            return toResponse(order);
        } catch (Exception e) {
            // 下单失败：删除幂等 key，允许前端用同一 requestId 重试；再抛出让 Seata 回滚, TODO
            // 可能redis缓存删掉了，但是我的seata还没删减完
            redisTemplate.delete(orderKeyPrefix + requestId);
            throw e;
        }
    }

    @Override
    public OrderResponse getOrder(Long orderId) {
        // 1. 本地 Caffeine 缓存（内存操作，不会抛）
        Order order = orderCache.getIfPresent(orderId);
        if (order != null) {
            checkOwner(order); // 缓存命中也要验归属，否则他人订单直接放行
            return toResponse(order);
        }

        // 2. Redis：挂了就降级查库，不能把读接口打挂
        try {
            order = (Order) redisTemplate.opsForValue().get(cacheKey(orderId));
            if (order != null) {
                checkOwner(order);
                orderCache.put(orderId, order);
                return toResponse(order);
            }
        } catch (Exception e) {
            log.warn("Redis 读缓存失败，降级查库 orderId={}", orderId, e);
        }

        // 3. MySQL：数据源，必须在 catch 外面
        order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new ApiException(ErrorCode.ORDER_NOT_FOUND);
        }
        checkOwner(order); // IDOR 核心防线：当前用户必须等于订单归属

        // 回填：写失败只影响下次命中，不影响本次返回
        orderCache.put(orderId, order);
        try {
            redisTemplate.opsForValue().set(cacheKey(orderId), order, Duration.ofMinutes(5));
        } catch (Exception e) {
            log.warn("Redis 写缓存失败，忽略 orderId={}", orderId, e);
        }
        return toResponse(order);
    }

    /** IDOR 防御：当前登录用户必须是订单归属者，否则 403 */
    private void checkOwner(Order order) {
        Long uid = UserContext.getUserId();
        if (uid == null || !uid.equals(order.getUserId())) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
    }

    /** 订单详情缓存 key：与幂等 key（create:order:{requestId}）分开命名空间 */
    private String cacheKey(Long orderId) {
        return "order:cache:" + orderId;
    }

    // 生成订单号
    private String generateOrderNo() {
        return IdWorker.getIdStr();
    }

    /** domain Order → 对外 OrderResponse 映射（service 层转换，controller 保持薄层） */
    private OrderResponse toResponse(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .userId(order.getUserId())
                .productId(order.getProductId())
                .quantity(order.getQuantity())
                .unitPrice(order.getUnitPrice())
                .amount(order.getAmount())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .build();
    }

}
