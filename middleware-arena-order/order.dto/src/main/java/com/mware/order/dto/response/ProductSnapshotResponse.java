package com.mware.order.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** order-service 从 product-service 读取的最小商品快照。 */
@Data
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductSnapshotResponse {

    private Long id;
    private String name;

    /** 商品单价，单位：分。 */
    private Long price;
}
