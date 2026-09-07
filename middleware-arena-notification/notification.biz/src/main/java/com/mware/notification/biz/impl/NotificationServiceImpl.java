package com.mware.notification.biz.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mware.common.web.ApiException;
import com.mware.common.web.ErrorCode;
import com.mware.common.web.UserContext;
import com.mware.notification.biz.NotificationService;
import com.mware.notification.domain.Notification;
import com.mware.notification.dto.request.NotificationRequest;
import com.mware.notification.dto.response.NotificationResponse;
import com.mware.notification.mapper.NotificationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** 站内通知持久化、未读状态和 SSE 在线推送。 */
@Service
public class NotificationServiceImpl implements NotificationService {

    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L;

    private final NotificationMapper notificationMapper;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /**
     * 注入通知持久化接口和 JSON 解析器。
     * @param notificationMapper 通知访问接口
     * @param objectMapper JSON 解析器
     */
    public NotificationServiceImpl(NotificationMapper notificationMapper, ObjectMapper objectMapper) {
        this.notificationMapper = notificationMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 保存实验完成通知，提交后向在线用户推送。
     * @param message 实验完成事件 JSON
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleExperimentCompleted(String message) {
        // 1. 校验事件并持久化通知，重复事件不重复发送。
        try {
            JsonNode event = objectMapper.readTree(message);
            Long userId = requiredLong(event, "userId");
            Long taskId = requiredLong(event, "taskId");
            long exists = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                    .eq(Notification::getUserId, userId)
                    .eq(Notification::getSourceType, "experiment")
                    .eq(Notification::getSourceId, taskId)
                    .eq(Notification::getType, "experiment_done"));
            if (exists > 0) return;

            NotificationRequest request = NotificationRequest.builder()
                    .type("experiment_done")
                    .sourceType("experiment")
                    .sourceId(taskId)
                    .title("实验运行完成")
                    .content(event.path("summary").asText("实验任务已完成，可查看运行结果"))
                    .build();
            Notification notification = saveNotification(userId, request);
            // 2. 提交后才推送，避免客户端收到随后回滚的通知。
            NotificationResponse response = toResponse(notification);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                /** 数据提交成功后推送通知。 */
                @Override
                public void afterCommit() {
                    sendToOnlineUser(userId, response);
                }
            });
        } catch (IOException | IllegalArgumentException e) {
            throw new ApiException(ErrorCode.PARAM_INVALID, "实验完成事件格式错误");
        }
    }

    /**
     * 为当前用户创建通知，提交成功后推送。
     * @param request 通知内容
     * @return 已保存的通知
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public NotificationResponse createNotification(NotificationRequest request) {
        // 1. 身份校验与通知入库在业务事务中完成。
        Long userId = UserContext.getUserId();
        if (userId == null) throw new ApiException(ErrorCode.UNAUTHORIZED);
        validateRequest(request);
        Notification notification = saveNotification(userId, request);
        NotificationResponse response = toResponse(notification);
        // 2. 外部 SSE 写入延后到提交成功，断线不影响数据库结果。
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /** 数据提交成功后推送通知。 */
            @Override
            public void afterCommit() {
                sendToOnlineUser(userId, response);
            }
        });
        return response;
    }

    /**
     * 将指定用户的通知标为已读。
     * @param notificationId 通知 ID
     * @param userId 通知所属用户 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long notificationId, Long userId) {
        // 1. 按用户归属查找通知，禁止修改他人的已读状态。
        if (notificationId == null || userId == null) throw new ApiException(ErrorCode.PARAM_INVALID);
        Notification notification = notificationMapper.selectOne(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getId, notificationId)
                .eq(Notification::getUserId, userId));
        if (notification == null) throw new ApiException(ErrorCode.NOT_FOUND);
        if (Boolean.TRUE.equals(notification.getIsRead())) return;
        // 2. 已读通知保持原阅读时间，仅首次阅读时更新。
        notification.setIsRead(true);
        notification.setReadAt(LocalDateTime.now());
        notificationMapper.updateById(notification);
    }

    /**
     * 分页查询用户通知。
     * @param userId 用户 ID
     * @param page 页码
     * @param size 每页数量
     * @return 本页通知
     */
    @Override
    public List<NotificationResponse> pageNotifications(Long userId, int page, int size) {
        if (userId == null) throw new ApiException(ErrorCode.UNAUTHORIZED);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        return notificationMapper.selectPage(new Page<>(safePage, safeSize),
                        new LambdaQueryWrapper<Notification>()
                                .eq(Notification::getUserId, userId)
                                .orderByDesc(Notification::getCreatedAt))
                .getRecords().stream().map(this::toResponse).toList();
    }

    /**
     * 查询用户未读通知数。
     * @param userId 用户 ID
     * @return 未读数量
     */
    @Override
    public long unreadCount(Long userId) {
        if (userId == null) throw new ApiException(ErrorCode.UNAUTHORIZED);
        return notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, false));
    }

    /**
     * 向在线用户发送即时通知，不持久化。
     * @param userId 接收用户 ID
     * @param request 通知内容
     */
    @Override
    public void push(Long userId, NotificationRequest request) {
        validateRequest(request);
        sendToOnlineUser(userId, request);
    }

    /**
     * 注册用户的 SSE 连接，并在连接结束时移除。
     * @param userId 已认证的用户 ID
     * @return 通知事件流
     */
    @Override
    public SseEmitter subscribe(Long userId) {
        // 1. 按用户原子注册，避免清理旧连接时移除刚加入的新连接。
        if (userId == null) throw new ApiException(ErrorCode.UNAUTHORIZED);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitters.compute(userId, (ignored, userEmitters) -> {
            if (userEmitters == null) userEmitters = new CopyOnWriteArrayList<>();
            userEmitters.add(emitter);
            return userEmitters;
        });
        // 2. 完成、超时和发送失败均清理注册信息。
        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> removeEmitter(userId, emitter));
        emitter.onError(error -> removeEmitter(userId, emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException | IllegalStateException e) {
            removeEmitter(userId, emitter);
        }
        return emitter;
    }

    private Notification saveNotification(Long userId, NotificationRequest request) {
        validateRequest(request);
        Notification notification = toEntity(request);
        notification.setUserId(userId);
        notification.setIsRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        notificationMapper.insert(notification);
        return notification;
    }

    private void validateRequest(NotificationRequest request) {
        if (request == null || request.getType() == null || request.getType().isBlank()) {
            throw new ApiException(ErrorCode.PARAM_INVALID, "通知类型不能为空");
        }
    }

    private Long requiredLong(JsonNode event, String field) {
        if (event == null || !event.isObject()) throw new IllegalArgumentException("事件必须是 JSON 对象");
        JsonNode value = event.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()
                || value.longValue() <= 0) throw new IllegalArgumentException(field);
        return value.longValue();
    }

    private void sendToOnlineUser(Long userId, Object data) {
        // 1. 获取并发安全的连接快照，允许连接同时订阅和关闭。
        List<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters == null) return;
        // 2. 单个连接失效不影响其他在线连接。
        for (SseEmitter emitter : userEmitters) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(data));
            } catch (IOException | IllegalStateException e) {
                removeEmitter(userId, emitter);
            }
        }
    }

    private void removeEmitter(Long userId, SseEmitter emitter) {
        emitters.computeIfPresent(userId, (ignored, userEmitters) -> {
            userEmitters.remove(emitter);
            return userEmitters.isEmpty() ? null : userEmitters;
        });
    }

    private Notification toEntity(NotificationRequest request) {
        return Notification.builder()
                .type(request.getType()).sourceType(request.getSourceType()).sourceId(request.getSourceId())
                .title(request.getTitle()).content(request.getContent()).build();
    }

    private NotificationResponse toResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId()).userId(notification.getUserId()).type(notification.getType())
                .sourceType(notification.getSourceType()).sourceId(notification.getSourceId())
                .title(notification.getTitle()).content(notification.getContent()).isRead(notification.getIsRead())
                .readAt(notification.getReadAt()).createdAt(notification.getCreatedAt()).build();
    }
}
