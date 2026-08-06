package com.mware.experiment.controller;

import com.mware.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 实验接口（骨架占位，返回统一 {@link ApiResponse}）。
 * <p>
 * TODO：
 *   1. POST /experiment/template 实验模板管理（CRUD）
 *   2. POST /experiment/version 版本快照（保存 / 回滚实验版本）
 *   3. POST /experiment/task    创建实验任务（拉起 runner）
 *   4. GET  /experiment/task/{id} 任务详情 + 进度
 *   5. GET  /experiment/task/{id}/progress SSE 实时进度推送
 *   6. 任务状态机：pending → queued → running → success/failed/cancelled
 */
@Tag(name = "实验")
@RestController
@RequestMapping("/experiment")
public class ExperimentController {

    @Operation(summary = "健康检查")
    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        return ApiResponse.ok("pong");
    }
}
