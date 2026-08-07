package com.mware.order.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
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

    private BigDecimal amount;

    private String status;

    private String requestId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
