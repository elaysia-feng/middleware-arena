package com.mware.order.biz;

import com.mware.order.dto.request.CreateOrderRequest;
import com.mware.order.dto.response.OrderResponse;

/**
 * 订单业务接口。
 * <p>
 * 方法签名已定，具体实现留待接入 Seata / Feign / Redis / RabbitMQ 后补齐。
 */
public interface OrderService {

    /** 创建订单：Feign 调 storage 扣库存 + account 扣余额（@GlobalTransactional） */
    OrderResponse createOrder(CreateOrderRequest request);

    /** 订单详情（Redis Cache-Aside 缓存） */
    OrderResponse getOrder(Long orderId);
}
