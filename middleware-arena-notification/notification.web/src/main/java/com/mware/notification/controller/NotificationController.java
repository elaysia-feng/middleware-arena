package com.mware.notification.controller;

import com.mware.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知接口（骨架占位，返回统一 {@link ApiResponse}）。
 * <p>
 * TODO：
 *   1. RabbitMQ 消费 experiment 完成事件 → 创建站内通知
 *   2. GET  /notification/list        分页获取当前用户通知列表
 *   3. PUT  /notification/read/{id}   标记已读
 *   4. GET  /notification/unread-count 未读数量
 *   5. WebSocket / SSE 实时推送新通知
 */
@Tag(name = "通知")
@RestController
@RequestMapping("/notification")
public class NotificationController {

    @Operation(summary = "健康检查")
    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        return ApiResponse.ok("pong");
    }
}
