package com.mware.notification.biz;

import com.mware.notification.dto.request.NotificationRequest;
import com.mware.notification.dto.response.NotificationResponse;

import java.util.List;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 通知业务接口。
 * <p>
 * 方法签名已定，具体实现留待接入 RabbitMQ / SSE / 持久化后补齐。
 * Service 层直接面向 DTO（XxxRequest / XxxResponse），domain 实体仅存在于 Service/mapper 内部。
 */
public interface NotificationService {

    /** RabbitMQ 消费：experiment.completed 事件 → 创建站内通知 */
    void handleExperimentCompleted(String message);

    /** 创建站内通知（userId 从 UserContext 取，不信任客户端） */
    NotificationResponse createNotification(NotificationRequest request);

    /** 标记已读 */
    void markRead(Long notificationId, Long userId);

    /** 分页获取当前用户通知列表 */
    List<NotificationResponse> pageNotifications(Long userId, int page, int size);

    /** 未读数量 */
    long unreadCount(Long userId);

    /** 实时推送新通知（SSE / WebSocket） */
    void push(Long userId, NotificationRequest request);

    /** 建立当前用户的 SSE 通知连接。 */
    SseEmitter subscribe(Long userId);
}
