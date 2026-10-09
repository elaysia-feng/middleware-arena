package com.mware.product.dto.request;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 修改商品请求。
 * <p>
 * 字段为 null 表示不修改该字段（MyBatis-Plus updateById 默认跳过 null 字段）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UpdateProductRequest {

    /** 商品名称 */
    private String name;

    /** 单价（单位：分） */
    private Long price;

    /** 商品描述 */
    private String description;
}
