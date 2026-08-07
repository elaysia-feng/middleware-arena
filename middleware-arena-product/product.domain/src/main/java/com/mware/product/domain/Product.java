package com.mware.product.domain;

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
 * 商品实体（骨架占位）。
 * <p>
 * 字段与 sql/init.sql 同步。单价 price 是 order 计算订单金额（amount = price × quantity）的数据源。
 */
@Data
@TableName("product")
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 单价（order 侧金额计算的基准） */
    private BigDecimal price;

    private String description;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
