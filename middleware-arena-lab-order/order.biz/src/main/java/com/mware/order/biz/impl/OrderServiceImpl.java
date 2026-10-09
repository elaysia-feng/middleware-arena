package com.mware.order.biz.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.mware.order.dto.response.ProductSnapshotResponse;
import com.mware.order.feign.AccountClient;
import com.mware.order.feign.ProductClient;
import com.mware.order.feign.StorageClient;
import com.mware.order.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.apache.seata.spring.annotation.GlobalTransactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;

/**
 * 订单业务实现。
 * <p>
 * createOrder / getOrder 提供幂等、缓存降级和归属校验，事务配置与后续扩展说明如下：
 * <ol>
 * <li>Seata 分布式事务收尾：建 undo_log 表（见 sql/init.sql）+ application.yml 开启 Seata AT；
 * storage / account 需作为参与方加入同一全局事务（参与方 DDL 已含 undo_log）。</li>
 * <li>RabbitMQ 异步下单通知：下单成功后投递订单事件（order.created）至 MQ，
 * 通知服务 / 邮件 / 站内信消费；注意消息投递与本地事务的一致性。</li>
 * <li>订单超时自动取消：定时任务（如 Spring {@code @Scheduled} / XXL-Job）扫描 CREATE 超 N 分钟
 * 未 PAID 的订单置 CANCEL，并回补库存（storage 加回）/ 余额（account 加回）；注意回补需再走一次分布式事务。</li>
 * <li>幂等保障：Redis 按用户和 requestId 防并发提交，数据库 uk_user_request 唯一索引兜底。</li>
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
    private final RedisTemplate<String, Object> redisTemplate;
    private final Cache<Long, Order> orderCache;

    /**
     * 创建订单，并通过 Seata 协调库存和余额扣减。
     * @param request 商品、数量及客户端幂等标识
     * @return 新订单或相同请求已创建的订单
     */
    @Override
    @GlobalTransactional(name = "create-order", rollbackFor = Exception.class)
    public OrderResponse createOrder(CreateOrderRequest request) {
        // 1. 校验业务参数和当前用户，拒绝超长幂等标识。
        if (request == null || request.getProductId() == null || request.getProductId() <= 0
                || request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new ApiException(ErrorCode.PARAM_INVALID);
        }

        String requestId = request.getRequestId();
        if (!StringUtils.hasText(requestId)) {
            requestId = UUID.randomUUID().toString().replace("-", "");
        }
        if (requestId.length() > 64) {
            throw new ApiException(ErrorCode.PARAM_INVALID, "请求标识不能超过 64 个字符");
        }
        // 身份一律从 UserContext 取，不信任请求体（CreateOrderRequest 已不含 userId）
        Long uid = UserContext.getUserId();
        if (uid == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }

        // 2. 数据库幂等记录不受 Redis 五分钟有效期限制。
        Order previous = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, uid)
                .eq(Order::getRequestId, requestId));
        if (previous != null) {
            return toResponse(previous);
        }
        String idempotencyKey = orderKeyPrefix + uid + ":" + requestId;
        String lockToken = UUID.randomUUID().toString();
        Boolean first = redisTemplate.opsForValue().setIfAbsent(idempotencyKey, lockToken, Duration.ofMinutes(5));
        if (!Boolean.TRUE.equals(first)) {
            Order existing = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                    .eq(Order::getUserId, uid)
                    .eq(Order::getRequestId, requestId));
            if (existing != null) {
                return toResponse(existing);
            }
            throw new ApiException(ErrorCode.PARAM_INVALID, "订单正在处理中，请勿重复提交");
        }

        try {
            ApiResponse<ProductSnapshotResponse> productResult = productClient.getProduct(request.getProductId());
            checkRemoteResult(productResult);
            ProductSnapshotResponse product = productResult.getData();
            if (product == null || product.getPrice() == null || product.getPrice() <= 0) {
                throw new ApiException(ErrorCode.PARAM_INVALID, "商品价格无效");
            }

            // 金额统一 Long（单位：分），避免浮点误差：单价分 × 数量
            long amount;
            try {
                amount = Math.multiplyExact(product.getPrice(), request.getQuantity().longValue());
            } catch (ArithmeticException e) {
                throw new ApiException(ErrorCode.PARAM_INVALID, "订单金额超出允许范围");
            }

            Order order = Order.builder()
                    .userId(uid)
                    .productId(request.getProductId())
                    .quantity(request.getQuantity())
                    .orderNo(generateOrderNo())
                    .requestId(requestId)
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
            checkRemoteResult(storageClient.deductStock(request.getProductId(), request.getQuantity()));

            // 4. accountClient.deductBalance(userId, amount) 扣余额，余额不足抛
            // ApiException(BALANCE_NOT_ENOUGH)
            checkRemoteResult(accountClient.deductBalance(uid, amount));

            return toResponse(order);
        } catch (Exception e) {
            // Redis 不在 Seata 事务内，失败时主动释放短期幂等键；MySQL 三库由 Seata 回滚。
            try {
                // 只释放本次持有的键，防止超时后删掉后续请求重新取得的锁。
                redisTemplate.execute(new DefaultRedisScript<Long>(
                        "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) end return 0",
                        Long.class), List.of(idempotencyKey), lockToken);
            } catch (RuntimeException cacheException) {
                log.warn("释放订单幂等键失败，等待 TTL 过期", cacheException);
            }
            throw e;
        }
    }

    /**
     * 查询当前用户的订单，缓存不可用时回源数据库。
     * @param orderId 订单 ID
     * @return 订单详情
     */
    @Override
    public OrderResponse getOrder(Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new ApiException(ErrorCode.PARAM_INVALID);
        }
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

    /** 订单详情缓存 key：与幂等 key（create:order:{userId}:{requestId}）分开命名空间 */
    private String cacheKey(Long orderId) {
        return "order:cache:" + orderId;
    }

    // 生成订单号
    private String generateOrderNo() {
        return IdWorker.getIdStr();
    }

    /** Feign 的业务异常仍以 HTTP 200 返回，必须检查统一响应码才能触发全局回滚。 */
    private void checkRemoteResult(ApiResponse<?> response) {
        if (response == null) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "下游服务无响应");
        }
        if (response.getCode() != 200) {
            throw new ApiException(response.getCode(), response.getMessage());
        }
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
