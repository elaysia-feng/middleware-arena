package com.mware.community.biz.outbox.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 事务性 Outbox Relay 实现：扫描 PENDING 事件 → 投递 RabbitMQ → 置 SENT。
 */
@Component
@Slf4j
public class OutboxRelayImpl implements OutboxRelay {

    private final EventOutboxMapper eventOutboxMapper;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${community.outbox.relay-batch-size:100}")
    private int batchSize;

    @Value("${community.outbox.confirm-timeout-ms:3000}")
    private long confirmTimeoutMs;

    public OutboxRelayImpl(EventOutboxMapper eventOutboxMapper, RabbitTemplate rabbitTemplate) {
        this.eventOutboxMapper = eventOutboxMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
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
