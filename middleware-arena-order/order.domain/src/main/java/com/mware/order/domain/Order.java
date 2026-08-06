package com.mware.order.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体（骨架占位）。
 * <p>
 * TODO[Seata 分布式事务]：字段与 sql/init.sql 同步。
 */
@Data
@TableName("`order`")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long productId;

    private Integer quantity;

    private BigDecimal amount;

    private String status;

    private LocalDateTime createdAt;
}
