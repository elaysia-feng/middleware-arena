package com.mware.community.biz.outbox.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mware.community.biz.config.RabbitLikeConfig;
import com.mware.community.biz.outbox.OutboxRelay;
import com.mware.community.domain.EventOutbox;
import com.mware.community.dto.message.LikeEvent;
import com.mware.community.mapper.EventOutboxMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 旧 MySQL Outbox，点赞已迁移 Redis Stream；仅给后续未迁移业务保留，默认关闭。
 *
 * <p><b>多实例安全（条件更新抢占）</b>：每条事件投递前先原子抢占——
 * {@code UPDATE event_outbox SET status='SENDING' WHERE id=? AND status IN (PENDING, FAILED)}，
 * 抢占成功（行数=1）的实例才真正投递，其余实例自然跳过，避免重复投递；
 * 实例投递中途崩溃时事件停留 SENDING，超过 reclaim 阈值后会被重新扫描回收重投。
 * 重复投递仍由消费者 event_id 幂等兜底。</p>
 */
@Component
@ConditionalOnProperty(prefix = "community.outbox", name = "enabled", havingValue = "true")
@Slf4j
public class OutboxRelayImpl implements OutboxRelay {
    private final EventOutboxMapper eventOutboxMapper;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Value("${community.outbox.relay-batch-size:100}") private int batchSize;
    @Value("${community.outbox.confirm-timeout-ms:3000}") private long confirmTimeoutMs;
    /** SENDING 状态超过该分钟数视为实例崩溃遗留，重新回收投递 */
    @Value("${community.outbox.reclaim-sending-minutes:10}") private long reclaimSendingMinutes;

    public OutboxRelayImpl(EventOutboxMapper eventOutboxMapper, RabbitTemplate rabbitTemplate) {
        this.eventOutboxMapper = eventOutboxMapper; this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    @Scheduled(fixedDelayString = "${community.outbox.relay-interval-ms:5000}")
    public void relay() {
        // 候选 = PENDING / FAILED；SENDING 超过回收阈值也重新入列（实例崩溃遗留）
        LocalDateTime sendingStaleBefore = LocalDateTime.now().minusMinutes(reclaimSendingMinutes);
        List<Long> candidateIds = eventOutboxMapper.selectList(new LambdaQueryWrapper<EventOutbox>()
                .select(EventOutbox::getId)
                .and(w -> w.in(EventOutbox::getStatus, EventOutbox.STATUS_PENDING, EventOutbox.STATUS_FAILED)
                        .or(ow -> ow.eq(EventOutbox::getStatus, EventOutbox.STATUS_SENDING)
                                .lt(EventOutbox::getCreatedAt, sendingStaleBefore)))
                .orderByAsc(EventOutbox::getId).last("LIMIT " + batchSize))
                .stream().map(EventOutbox::getId).toList();

        for (Long id : candidateIds) {
            // 条件更新抢占：只有一个实例能把状态改成 SENDING，输家直接跳过
            int claimed = eventOutboxMapper.update(null, new LambdaUpdateWrapper<EventOutbox>()
                    .eq(EventOutbox::getId, id)
                    .in(EventOutbox::getStatus, EventOutbox.STATUS_PENDING, EventOutbox.STATUS_FAILED)
                    .set(EventOutbox::getStatus, EventOutbox.STATUS_SENDING));
            if (claimed != 1) {
                continue;
            }
            EventOutbox row = eventOutboxMapper.selectById(id);
            if (row != null) {
                send(row);
            }
        }
    }

    private void send(EventOutbox row) {
        try {
            LikeEvent event = objectMapper.readValue(row.getPayload(), LikeEvent.class);
            CorrelationData data = new CorrelationData(row.getEventId());
            rabbitTemplate.convertAndSend(RabbitLikeConfig.EXCHANGE_LIKE, "", event, data);
            CorrelationData.Confirm confirm = data.getFuture().get(confirmTimeoutMs, TimeUnit.MILLISECONDS);
            if (confirm != null && confirm.isAck()) { row.setStatus(EventOutbox.STATUS_SENT); row.setSentAt(LocalDateTime.now()); }
            else row.setStatus(EventOutbox.STATUS_FAILED);
            eventOutboxMapper.updateById(row);
        } catch (Exception e) {
            log.error("legacy outbox relay failed: eventId={}", row.getEventId(), e);
            row.setStatus(EventOutbox.STATUS_FAILED); eventOutboxMapper.updateById(row);
        }
    }
}
