package com.mware.order.feign;

import com.mware.common.web.ApiResponse;
import com.mware.product.domain.Product;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 调用 product-service 的 OpenFeign 客户端（框架占位）。
 * <p>
 * 下单时通过它查询商品单价 price，用于计算订单金额 amount = price × quantity。
 */
@FeignClient(name = "product-service")
public interface ProductClient {

    /**
     * 查询商品（含单价）。
     *
     * @param productId 商品 ID
     * @return 商品信息（不存在时 product 侧抛 ApiException，Feign 透传）
     */
    @GetMapping("/product/{productId}")
    ApiResponse<Product> getProduct(@PathVariable("productId") Long productId);
}
