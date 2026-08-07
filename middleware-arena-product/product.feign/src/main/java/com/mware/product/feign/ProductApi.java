package com.mware.product.feign;

import com.mware.common.web.ApiResponse;
import com.mware.product.domain.Product;

/**
 * 商品 HTTP 契约（被调方侧，仅定义端点签名）。
 * <p>
 * product 是被调方，真正的 Feign 客户端由调用方（order-service 的 order.feign）定义；
 * 此接口用于对齐端点契约（GET /product/{productId}）。
 */
public interface ProductApi {

    /**
     * 查询商品（含单价）。
     *
     * @param productId 商品 ID
     * @return 商品信息（不存在时抛 ApiException(PRODUCT_NOT_FOUND)）
     */
    ApiResponse<Product> getProduct(Long productId);
}
