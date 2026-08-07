package com.mware.storage.feign;

import com.mware.common.web.ApiResponse;

/**
 * 库存 HTTP 契约（被调方侧，仅定义端点签名）。
 * <p>
 * storage 是被调方，真正的 Feign 客户端由调用方（order-service 的 order.feign）定义；
 * 此接口用于对齐端点契约（POST /storage/deduct）。
 */
public interface InventoryApi {

    /**
     * 扣减库存。
     *
     * @param productId 商品 ID
     * @param quantity  扣减数量
     * @return 扣减结果（库存不足时抛 ApiException(STOCK_NOT_ENOUGH)，由 GlobalExceptionHandler 转响应）
     */
    ApiResponse<Void> deductStock(Long productId, Integer quantity);
}
