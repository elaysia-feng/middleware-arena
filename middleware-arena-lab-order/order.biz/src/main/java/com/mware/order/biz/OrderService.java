package com.mware.order.biz;

import com.mware.order.dto.request.CreateOrderRequest;
import com.mware.order.dto.response.OrderResponse;

/**
 * 订单业务接口。
 * <p>
 * 创建订单由 Seata AT 统一协调订单、库存和账户三个业务库。
 */
public interface OrderService {

    /** 创建订单：Feign 调 storage 扣库存 + account 扣余额（@GlobalTransactional） */
    OrderResponse createOrder(CreateOrderRequest request);

    /** 订单详情（Redis Cache-Aside 缓存） */
    OrderResponse getOrder(Long orderId);
}
