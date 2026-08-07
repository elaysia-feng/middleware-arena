package com.mware.runner.controller;

import com.mware.common.web.ApiResponse;
import com.mware.runner.biz.RunnerService;
import com.mware.runner.domain.RunnerTask;
import com.mware.runner.dto.request.RunRequest;
import com.mware.runner.dto.response.TaskResponse;
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
 * TODO[Runner]：接入 MQ / docker / k6 后，各环节在 {@link RunnerService} 内实现。
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

    @Operation(summary = "拉取待执行任务")
    @PostMapping("/pull")
    public ApiResponse<TaskResponse> pull() {
        return ApiResponse.ok(toResult(runnerService.pullTask()));
    }

    @Operation(summary = "构建中间件镜像 / 二进制")
    @PostMapping("/build")
    public ApiResponse<TaskResponse> build(@RequestBody RunRequest request) {
        return ApiResponse.ok(toResult(runnerService.build(toTask(request))));
    }

    @Operation(summary = "启动 Docker 容器（给定版本 + 配置）")
    @PostMapping("/run")
    public ApiResponse<TaskResponse> run(@RequestBody RunRequest request) {
        return ApiResponse.ok(toResult(runnerService.run(toTask(request))));
    }

    @Operation(summary = "执行 k6 压测（HTTP / gRPC / TCP）")
    @PostMapping("/benchmark")
    public ApiResponse<TaskResponse> benchmark(@RequestBody RunRequest request) {
        return ApiResponse.ok(toResult(runnerService.benchmark(toTask(request))));
    }

    @Operation(summary = "采集指标（CPU / 内存 / 延迟 / QPS）")
    @PostMapping("/collect")
    public ApiResponse<TaskResponse> collect(@RequestBody RunRequest request) {
        return ApiResponse.ok(toResult(runnerService.collectMetrics(toTask(request))));
    }

    @Operation(summary = "清理：停止容器、删除临时镜像、释放端口")
    @PostMapping("/cleanup")
    public ApiResponse<Void> cleanup(@RequestBody RunRequest request) {
        runnerService.cleanup(toTask(request));
        return ApiResponse.ok();
    }

    @Operation(summary = "完整流水线执行：pull → build → run → bench → collect → cleanup")
    @PostMapping("/execute")
    public ApiResponse<TaskResponse> execute(@RequestBody RunRequest request) {
        return ApiResponse.ok(toResult(runnerService.execute(toTask(request))));
    }

    @Operation(summary = "查询任务状态")
    @GetMapping("/task/{taskId}")
    public ApiResponse<TaskResponse> getTask(@PathVariable String taskId) {
        return ApiResponse.ok(toResult(runnerService.getTask(taskId)));
    }

    /** 请求 → 领域对象（框架胶水，字段后续按需补全） */
    private RunnerTask toTask(RunRequest request) {
        return RunnerTask.builder()
                .taskId(request.getTaskId())
                .build();
    }

    /** 领域对象 → 响应 DTO（框架胶水） */
    private TaskResponse toResult(RunnerTask task) {
        if (task == null) {
            return null;
        }
        return TaskResponse.builder()
                .id(task.getId())
                .taskId(task.getTaskId())
                .middlewareType(task.getMiddlewareType())
                .config(task.getConfig())
                .status(task.getStatus())
                .metrics(task.getMetrics())
                .createdAt(task.getCreatedAt())
                .build();
    }
}
