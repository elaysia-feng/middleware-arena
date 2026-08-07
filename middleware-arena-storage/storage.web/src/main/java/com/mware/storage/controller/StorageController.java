package com.mware.storage.controller;

import com.mware.common.web.ApiResponse;
import com.mware.storage.biz.StorageService;
import com.mware.storage.domain.Stock;
import com.mware.storage.dto.response.StockResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 库存接口（骨架占位，返回统一 {@link ApiResponse}）。
 * <p>
 * TODO[Seata AT 参与方]：
 *   1. POST /storage/deduct 扣减库存（Seata AT 参与方，由 order-service 通过 Feign 调用）
 *   2. undo_log 表需随 Seata AT 启用而创建（Seata AT 自动回滚用）
 */
@Tag(name = "库存")
@RestController
@RequestMapping("/storage")
public class StorageController {

    private final StorageService storageService;

    public StorageController(StorageService storageService) {
        this.storageService = storageService;
    }

    @Operation(summary = "健康检查")
    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        return ApiResponse.ok("pong");
    }

    /**
     * 扣减库存（框架占位）。
     * <p>
     * 本端点是 <b>内部服务间端点</b>，由 order-service 通过 Feign 调用。productId / quantity 均为业务参数
     * （非调用者身份），防绕过网关直连伪造依赖 base.web 的 AuthHeaderInterceptor（配 {@code ma.internal-auth.secret}
     * 后校验 X-User-Id / X-Sign HMAC 签名，未带合法签名的直连请求返回 401）。启用分布式事务时请配合 Seata AT。
     */
    @Operation(summary = "扣减库存（内部服务间调用）")
    @PostMapping("/deduct")
    public ApiResponse<Void> deductStock(@RequestParam("productId") Long productId,
                                         @RequestParam("quantity") Integer quantity) {
        storageService.deductStock(productId, quantity);
        return ApiResponse.ok();
    }

    @Operation(summary = "查询库存")
    @GetMapping("/stock/{productId}")
    public ApiResponse<StockResponse> getStock(@PathVariable("productId") Long productId) {
        Stock stock = storageService.getStock(productId);
        StockResponse response = StockResponse.builder()
                .productId(stock.getProductId())
                .quantity(stock.getQuantity())
                .build();
        return ApiResponse.ok(response);
    }
}
