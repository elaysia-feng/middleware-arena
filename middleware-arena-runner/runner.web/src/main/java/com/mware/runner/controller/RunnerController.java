package com.mware.runner.controller;

import com.mware.common.web.ApiResponse;
import com.mware.runner.biz.execution.RunnerService;
import com.mware.runner.dto.RunnerTaskMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Runner 接口（框架占位，返回统一 {@link ApiResponse}）。
 * <p>
 * runner 为消息驱动：正常由 experiment-service 投递 {@link RunnerTaskMessage} 至 RabbitMQ，
 * runner 消费执行；此处保留 HTTP 端点便于联调 / 手动触发。
 */
@Tag(name = "Runner")
@RestController
@RequestMapping("/runner")
public class RunnerController {

    private final RunnerService runnerService;

    public RunnerController(RunnerService runnerService) {
        this.runnerService = runnerService;
    }

    @Operation(summary = "健康检查")
    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        return ApiResponse.ok("pong");
    }

    @Operation(summary = "执行完整流水线：build → run → benchmark → collect → cleanup")
    @PostMapping("/execute")
    public ApiResponse<RunnerTaskMessage> execute(@RequestBody RunnerTaskMessage message) {
        return ApiResponse.ok(runnerService.execute(message));
    }

    @Operation(summary = "取消本地运行中的任务（联调 / 手动触发；正常取消由 CancelConsumer 走定向队列）")
    @PostMapping("/task/{taskId}/cancel")
    public ApiResponse<Boolean> cancel(@PathVariable Long taskId) {
        return ApiResponse.ok(runnerService.cancelTask(taskId));
    }

    @Operation(summary = "查询任务进度（状态由 experiment-service 持有）")
    @GetMapping("/task/{taskId}/status")
    public ApiResponse<String> getTaskStatus(@PathVariable Long taskId) {
        return ApiResponse.ok(runnerService.getTaskStatus(taskId));
    }
}
