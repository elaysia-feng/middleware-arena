package com.mware.order.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单响应对象（对外暴露，含展示所需字段）。
 */
@Data
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class OrderResponse {

    private Long id;

    /** 对外订单号 */
    private String orderNo;

    /** 下单用户 ID */
    private Long userId;

    /** 商品 ID */
    private Long productId;

    /** 购买数量 */
    private Integer quantity;

    /** 订单金额 */
    private BigDecimal amount;

    /** 订单状态（CREATE / PAID / CANCEL，见 {@link com.mware.order.domain.OrderStatus}） */
    private String status;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
