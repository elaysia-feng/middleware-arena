package com.mware.order.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 订单实体（骨架占位）。
 * <p>
 * TODO[Seata 分布式事务]：字段与 sql/init.sql 同步。
 */
@Data
@TableName("`order`")
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 下单用户 ID（扣余额 / 归属定位） */
    private Long userId;
    // 对外订单号
    private String orderNo;

    private Long productId;

    private Integer quantity;

    /** 下单时商品单价快照（单位：分），商品改价不影响历史订单 */
    private Long unitPrice;

    /** 总金额（单位：分）= unitPrice × quantity */
    private Long amount;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
