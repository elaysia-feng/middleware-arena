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

    public NotificationServiceImpl(NotificationMapper notificationMapper, ObjectMapper objectMapper) {
        this.notificationMapper = notificationMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleExperimentCompleted(String message) {
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
            sendToOnlineUser(userId, toResponse(notification));
        } catch (IOException | IllegalArgumentException e) {
            throw new ApiException(ErrorCode.PARAM_INVALID, "实验完成事件格式错误");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NotificationResponse createNotification(NotificationRequest request) {
        Long userId = UserContext.getUserId();
        if (userId == null) throw new ApiException(ErrorCode.UNAUTHORIZED);
        validateRequest(request);
        Notification notification = saveNotification(userId, request);
        NotificationResponse response = toResponse(notification);
        sendToOnlineUser(userId, response);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long notificationId, Long userId) {
        if (notificationId == null || userId == null) throw new ApiException(ErrorCode.PARAM_INVALID);
        Notification notification = notificationMapper.selectOne(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getId, notificationId)
                .eq(Notification::getUserId, userId));
        if (notification == null) throw new ApiException(ErrorCode.NOT_FOUND);
        if (Boolean.TRUE.equals(notification.getIsRead())) return;
        notification.setIsRead(true);
        notification.setReadAt(LocalDateTime.now());
        notificationMapper.updateById(notification);
    }

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

    @Override
    public long unreadCount(Long userId) {
        if (userId == null) throw new ApiException(ErrorCode.UNAUTHORIZED);
        return notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, false));
    }

    @Override
    public void push(Long userId, NotificationRequest request) {
        validateRequest(request);
        sendToOnlineUser(userId, request);
    }

    @Override
    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitters.computeIfAbsent(userId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> removeEmitter(userId, emitter));
        emitter.onError(error -> removeEmitter(userId, emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
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
        JsonNode value = event.get(field);
        if (value == null || !value.canConvertToLong()) throw new IllegalArgumentException(field);
        return value.longValue();
    }

    private void sendToOnlineUser(Long userId, Object data) {
        List<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters == null) return;
        for (SseEmitter emitter : userEmitters) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(data));
            } catch (IOException e) {
                removeEmitter(userId, emitter);
            }
        }
    }

    private void removeEmitter(Long userId, SseEmitter emitter) {
        List<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters == null) return;
        userEmitters.remove(emitter);
        if (userEmitters.isEmpty()) emitters.remove(userId);
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
