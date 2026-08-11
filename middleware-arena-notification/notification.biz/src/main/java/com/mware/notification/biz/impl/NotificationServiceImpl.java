package com.mware.notification.biz.impl;

import com.mware.notification.biz.NotificationService;
import com.mware.notification.domain.Notification;
import com.mware.notification.dto.request.NotificationRequest;
import com.mware.notification.dto.response.NotificationResponse;
import com.mware.notification.mapper.NotificationMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 通知业务实现（骨架占位）。
 * <p>
 * TODO[通知]：接入 RabbitMQ + SSE + 数据源后，按各方法 1.2.3. 编号步骤逐个实现。
 * Request→domain 与 domain→Response 映射在 Service 层完成，Controller 不接触 domain。
 */
@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;

    public NotificationServiceImpl(NotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    @Override
    public void handleExperimentCompleted(String message) {
        // TODO[通知]：消费 experiment.completed 事件 → 创建站内通知
        //   1. 解析 message（JSON：taskId / status / 结果摘要）
        //   2. 组装 NotificationRequest{type=experiment_done, sourceType=experiment, sourceId=taskId, title, content}，
        //      userId 从 UserContext 或 message 取（MQ 事件需携带接收者 userId）
        //   3. 落库 notificationMapper.insert(notification)，再走 push() 实时推送
        //   4. 幂等防重复消费：以 sourceId（taskId）判重（source_type + source_id 有联合索引 idx_source）
    }

    @Override
    public NotificationResponse createNotification(NotificationRequest request) {
        // TODO[通知]：创建站内信
        //   1. 校验 type 非空，非法抛 ApiException(PARAM_INVALID)
        //   2. Notification notification = toEntity(request)；userId 从 UserContext 取（null 抛 ApiException(UNAUTHORIZED)）
        //   3. 补默认值：isRead=false、createdAt=now
        //   4. notificationMapper.insert(notification)，回填自增 id
        //   5. 返回 toResponse(notification)
        return null;
    }

    @Override
    public void markRead(Long notificationId, Long userId) {
        // TODO[通知]：标记已读
        //   1. 归属校验（防 IDOR）：selectOne(id=notificationId AND user_id=userId)，
        //      查不到抛 ApiException(NOT_FOUND)——他人通知不可读/改
        //   2. 置 isRead=true、readAt=now，notificationMapper.updateById(notification)
    }

    @Override
    public List<NotificationResponse> pageNotifications(Long userId, int page, int size) {
        // TODO[通知]：通知分页列表
        //   1. 分页参数兜底：page >= 1、1 <= size <= 50
        //   2. notificationMapper.selectPage(Page(page, size),
        //        new LambdaQueryWrapper<Notification>()
        //            .eq(Notification::getUserId, userId)
        //            .orderByDesc(Notification::getCreatedAt))
        //   3. records 逐个 toResponse 后返回
        return null;
    }

    @Override
    public long unreadCount(Long userId) {
        // TODO[通知]：未读数
        //   1. notificationMapper.selectCount(
        //        new LambdaQueryWrapper<Notification>()
        //            .eq(Notification::getUserId, userId)
        //            .eq(Notification::getIsRead, false))
        return 0;
    }

    @Override
    public void push(Long userId, NotificationRequest request) {
        // TODO[通知]：实时推送（SSE / WebSocket）
        //   1. 维护 userId → 连接会话表（内存或 Redis）
        //   2. 在线则实时推送（toEntity(request) 后推送），离线不推（落库后由前端轮询兜底）
        //   3. 通道抽象：预留 PushHandler 接口，支持 WebSocket / SSE / 邮件多种实现
    }

    /** NotificationRequest → Notification（userId 由调用方 / UserContext 提供） */
    private Notification toEntity(NotificationRequest request) {
        return Notification.builder()
                .type(request.getType())
                .sourceType(request.getSourceType())
                .sourceId(request.getSourceId())
                .title(request.getTitle())
                .content(request.getContent())
                .build();
    }

    /** Notification → NotificationResponse（映射在 Service 层，Controller 不再关心 domain） */
    private NotificationResponse toResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .userId(notification.getUserId())
                .type(notification.getType())
                .sourceType(notification.getSourceType())
                .sourceId(notification.getSourceId())
                .title(notification.getTitle())
                .content(notification.getContent())
                .isRead(notification.getIsRead())
                .readAt(notification.getReadAt())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
