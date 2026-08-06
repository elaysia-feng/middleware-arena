package com.mware.runner.controller;

import com.mware.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Runner 接口（骨架占位，返回统一 {@link ApiResponse}）。
 * <p>
 * TODO：
 *   1. POST /runner/task/pull   拉取待执行任务（从 experiment-service 或消息队列）
 *   2. POST /runner/task/build  构建中间件镜像 / 二进制
 *   3. POST /runner/task/run    启动 Docker 容器（给定中间件版本 + 配置）
 *   4. POST /runner/task/bench  执行 k6 压测（HTTP / gRPC / TCP）
 *   5. POST /runner/task/collect 采集指标（CPU / 内存 / 延迟 / QPS）
 *   6. POST /runner/task/cleanup 清理容器 + 临时资源
 *   7. 完整流水线编排：pull → build → run → bench → collect → cleanup
 *   8. GET  /runner/task/{id}   查询任务状态
 */
@Tag(name = "Runner")
@RestController
@RequestMapping("/runner")
public class RunnerController {

    @Operation(summary = "健康检查")
    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        return ApiResponse.ok("pong");
    }
}
