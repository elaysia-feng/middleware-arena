package com.mware.product.controller;

import com.mware.common.web.ApiResponse;
import com.mware.product.biz.ProductService;
import com.mware.product.domain.Product;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品接口（骨架占位，返回统一 {@link ApiResponse}）。
 * <p>
 * GET /product/{productId} 为商品查询端点，order-service 通过 Feign 调用拿单价，
 * 用于计算订单金额 amount = price × quantity。
 */
@Tag(name = "商品")
@RestController
@RequestMapping("/product")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @Operation(summary = "健康检查")
    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        return ApiResponse.ok("pong");
    }

    @Operation(summary = "查询商品（含单价）")
    @GetMapping("/{productId}")
    public ApiResponse<Product> getProduct(@PathVariable("productId") Long productId) {
        return ApiResponse.ok(productService.getProduct(productId));
    }
}
