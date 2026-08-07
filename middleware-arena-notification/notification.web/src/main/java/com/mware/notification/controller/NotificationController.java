package com.mware.notification.controller;

import com.mware.common.web.ApiException;
import com.mware.common.web.ApiResponse;
import com.mware.common.web.ErrorCode;
import com.mware.common.web.UserContext;
import com.mware.notification.biz.NotificationService;
import com.mware.notification.dto.request.NotificationRequest;
import com.mware.notification.dto.response.NotificationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 通知接口（骨架占位，返回统一 {@link ApiResponse}）。
 * <p>
 * TODO：
 *   1. RabbitMQ 消费 experiment 完成事件 → 创建站内通知
 *      （POST /notification/experiment-completed 为手动触发测试端点）
 *   2. GET  /notification/list        分页获取当前用户通知列表
 *   3. POST /notification/{id}/read   标记已读
 *   4. GET  /notification/unread-count 未读数量
 *   5. WebSocket / SSE 实时推送新通知
 *      （POST /notification/push 为手动触发测试端点）
 */
@Tag(name = "通知")
@RestController
@RequestMapping("/notification")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Operation(summary = "健康检查")
    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        return ApiResponse.ok("pong");
    }

    @Operation(summary = "处理实验完成事件（RabbitMQ 消费 / 手动触发）")
    @PostMapping("/experiment-completed")
    public ApiResponse<Void> handleExperimentCompleted(@RequestBody String message) {
        notificationService.handleExperimentCompleted(message);
        return ApiResponse.ok();
    }

    @Operation(summary = "创建站内通知")
    @PostMapping("/send")
    public ApiResponse<NotificationResponse> createNotification(@RequestBody NotificationRequest request) {
        // 接收者身份以 UserContext 为准（Service 内取），防止伪造通知投递对象
        return ApiResponse.ok(notificationService.createNotification(request));
    }

    @Operation(summary = "标记已读")
    @PostMapping("/{notificationId}/read")
    public ApiResponse<Void> markRead(@PathVariable Long notificationId) {
        notificationService.markRead(notificationId, currentUserId());
        return ApiResponse.ok();
    }

    @Operation(summary = "分页获取通知列表")
    @GetMapping("/list")
    public ApiResponse<List<NotificationResponse>> pageNotifications(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(notificationService.pageNotifications(currentUserId(), page, size));
    }

    @Operation(summary = "未读数量")
    @GetMapping("/unread-count")
    public ApiResponse<Long> unreadCount() {
        return ApiResponse.ok(notificationService.unreadCount(currentUserId()));
    }

    @Operation(summary = "实时推送新通知（SSE / WebSocket / 手动触发）")
    @PostMapping("/push")
    public ApiResponse<Void> push(@RequestBody NotificationRequest request) {
        // 推送目标以 UserContext 为准，防止伪造接收者
        notificationService.push(currentUserId(), request);
        return ApiResponse.ok();
    }

    /**
     * 从 {@link UserContext} 取当前登录用户 ID；未登录（未过网关 / 直连服务端口）直接抛 401。
     */
    private Long currentUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
