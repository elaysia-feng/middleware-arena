package com.mware.community.biz.outbox;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mware.community.biz.config.RabbitLikeConfig;
import com.mware.community.domain.EventOutbox;
import com.mware.community.dto.message.LikeEvent;
import com.mware.community.mapper.EventOutboxMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 事务性 Outbox Relay：扫描 event_outbox 中 PENDING 的事件 → 投递 RabbitMQ → 置 SENT。
 * <p>
 * <b>为什么能可靠投递</b>：点赞写库与 outbox 写入同事务（见 CommunityServiceImpl.like），
 * 本地事务提交后事件已落库；本组件定时重扫，天然补偿"DB 成功但 MQ 失败/未确认"的场景。
 * <pre>
 *   查 PENDING(outbox) → convertAndSend(fanout) → 等 Publisher Confirm → SENT / FAILED
 * </pre>
 * <ul>
 *   <li><b>Publisher Confirm</b>：等待 Broker 持久化确认后再置 SENT，避免"发出即删"丢事件。
 *       依赖 {@code spring.rabbitmq.publisher-confirm-type=correlated}（application.yml，默认注释）。</li>
 *   <li><b>Mandatory/Return</b>：fanout 交换机不可路由场景少；不可路由消息触发 ReturnsCallback 记录。</li>
 *   <li><b>重复投递</b>：confirm 超时被标记 FAILED → 下次重扫重投，可能重复投递；
 *       由消费者 consumer_event 幂等兜底。</li>
 *   <li><b>事件回放</b>：event_outbox 保留不物理删除，Redis 全丢可据此重建；超期归档 MinIO（冷热分层）。</li>
 * </ul>
 * TODO[高并发]：本组件单实例扫描。多实例需先 SELECT ... FOR UPDATE SKIP LOCKED 或条件更新
 *   （UPDATE event_outbox SET status='SENDING' WHERE id=? AND status='PENDING'）防重复投递；
 *   分库分表后需按分片扫描（aggregate_id 分片）。
 */
@Component
@Slf4j
public class OutboxRelay {

    private final EventOutboxMapper eventOutboxMapper;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${community.outbox.relay-batch-size:100}")
    private int batchSize;

    @Value("${community.outbox.confirm-timeout-ms:3000}")
    private long confirmTimeoutMs;

    public OutboxRelay(EventOutboxMapper eventOutboxMapper, RabbitTemplate rabbitTemplate) {
        this.eventOutboxMapper = eventOutboxMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelayString = "${community.outbox.relay-interval-ms:5000}")
    public void relay() {
        List<EventOutbox> pending = eventOutboxMapper.selectList(new LambdaQueryWrapper<EventOutbox>()
                .eq(EventOutbox::getStatus, EventOutbox.STATUS_PENDING)
                .orderByAsc(EventOutbox::getId)
                .last("LIMIT " + batchSize));
        if (pending.isEmpty()) {
            return;
        }
        log.info("outbox relay: {} pending events", pending.size());
        for (EventOutbox row : pending) {
            send(row);
        }
    }

    /** 单条事件投递：反序列化 → 发布 → 等 confirm → 更新状态 */
    private void send(EventOutbox row) {
        try {
            LikeEvent event = objectMapper.readValue(row.getPayload(), LikeEvent.class);
            CorrelationData correlationData = new CorrelationData(row.getEventId());
            // fanout 交换机忽略 routingKey，传空串即可
            rabbitTemplate.convertAndSend(RabbitLikeConfig.EXCHANGE_LIKE, "", event, correlationData);

            // 等待 Publisher Confirm（Broker 已持久化）再置 SENT；超时置 FAILED 由下次扫描重投
            CorrelationData.Confirm confirm = correlationData.getFuture().get(confirmTimeoutMs, TimeUnit.MILLISECONDS);
            if (confirm != null && confirm.isAck()) {
                row.setStatus(EventOutbox.STATUS_SENT);
                row.setSentAt(LocalDateTime.now());
                eventOutboxMapper.updateById(row);
            } else {
                row.setStatus(EventOutbox.STATUS_FAILED);
                eventOutboxMapper.updateById(row);
            }
        } catch (Exception e) {
            log.error("outbox relay send failed: eventId={}, postId={}, type={}",
                    row.getEventId(), row.getAggregateId(), row.getEventType(), e);
            row.setStatus(EventOutbox.STATUS_FAILED);
            eventOutboxMapper.updateById(row);
        }
    }
}
