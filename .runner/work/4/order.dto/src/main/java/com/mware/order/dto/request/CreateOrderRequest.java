package com.mware.order.dto.request;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建订单请求。
 * <p>
 * 下单用户 ID 由服务端从 {@link com.mware.common.web.UserContext} 获取，不信任客户端。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreateOrderRequest {

    /** 商品 ID */
    private Long productId;

    /** 购买数量 */
    private Integer quantity;

    /** 前端传给后端的本次请求的标记 */
    private String requestId;
}
