package com.mware.community.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 事务性 Outbox 事件实体（@TableName = event_outbox）。
 * <p>
 * <b>Transactional Outbox 模式</b>：业务事实（post_like）与事件（event_outbox）<b>同事务双写</b>，
 * 本地事务提交即"事件已入账"。OutboxRelay 扫描 PENDING → 投递 RabbitMQ → 置 SENT。
 * <ul>
 *   <li>DB 成功但 MQ 失败：事件仍在 outbox，下次扫描重投（不会丢事件）。</li>
 *   <li>MQ 成功但 confirm 未确认（网络抖动）：重投可能重复 → 消费者靠 event_id + consumer_event 幂等。</li>
 *   <li>事件保留不物理删除：可回放重建 Redis（恢复不依赖 RabbitMQ 历史消息），超期归档 MinIO（冷热分层）。</li>
 * </ul>
 * ★ 分库分表目标表：与 post_like 同分片规则（aggregate_id = postId），保证"事实 + 事件"同片。
 */
@Data
@TableName("event_outbox")
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventOutbox {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 事件唯一 ID（业务生成 UUID），消费者幂等依据（consumer_event.event_id 对齐） */
    private String eventId;

    /** 聚合根 ID（= postId，分片键，与 post_like 同片） */
    private Long aggregateId;

    /** 事件类型：LIKE / UNLIKE / FAVORITE / UNFAVORITE / COMMENT */
    private String eventType;

    /** 事件体 JSON（LikeEvent 序列化） */
    private String payload;

    /** 状态：PENDING 待发送 / SENT 已投递 / FAILED 投递失败 */
    private String status;

    private LocalDateTime createdAt;

    /** 投递成功时间（status=SENT 时写入） */
    private LocalDateTime sentAt;
}
