package com.mware.community.dto.message;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 点赞事件消息契约（like.event.exchange 的 RabbitMQ 消息体，非数据库实体）。
 * <p>
 * 由 OutboxRelay 从 {@code event_outbox.payload} 反序列化后投递到 fanout 交换机，
 * 三个消费者（count / cache / statistics）各取所需：
 * <pre>
 * {
 *   "eventId":   "01J...",      // 全局唯一，消费者幂等依据
 *   "postId":    1001,
 *   "userId":    2001,
 *   "action":    "LIKE",        // LIKE / UNLIKE
 *   "timestamp": 1786123456     // 事件产生时间（epoch 秒）
 * }
 * </pre>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LikeEvent {

    public static final String ACTION_LIKE = "LIKE";
    public static final String ACTION_UNLIKE = "UNLIKE";

    /** 事件唯一 ID（对应 event_outbox.event_id，UUID），消费者幂等依据 */
    private String eventId;

    /** 帖子 ID */
    private Long postId;

    /** 操作用户 ID */
    private Long userId;

    /** 动作：LIKE / UNLIKE */
    private String action;

    /** 事件产生时间（epoch 秒） */
    private Long timestamp;
}
