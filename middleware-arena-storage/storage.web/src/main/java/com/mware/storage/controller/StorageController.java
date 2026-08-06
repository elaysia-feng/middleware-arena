package com.mware.storage.controller;

import com.mware.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @Operation(summary = "健康检查")
    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        return ApiResponse.ok("pong");
    }
}
