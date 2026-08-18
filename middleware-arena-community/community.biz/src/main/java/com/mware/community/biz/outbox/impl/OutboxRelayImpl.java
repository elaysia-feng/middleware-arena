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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** 旧 MySQL Outbox，点赞已迁移 Redis Stream；仅给后续未迁移业务保留，默认关闭。 */
@Component
@ConditionalOnProperty(prefix = "community.outbox", name = "enabled", havingValue = "true")
@Slf4j
public class OutboxRelayImpl implements OutboxRelay {
    private final EventOutboxMapper eventOutboxMapper;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Value("${community.outbox.relay-batch-size:100}") private int batchSize;
    @Value("${community.outbox.confirm-timeout-ms:3000}") private long confirmTimeoutMs;

    public OutboxRelayImpl(EventOutboxMapper eventOutboxMapper, RabbitTemplate rabbitTemplate) {
        this.eventOutboxMapper = eventOutboxMapper; this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    @Scheduled(fixedDelayString = "${community.outbox.relay-interval-ms:5000}")
    public void relay() {
        List<EventOutbox> rows = eventOutboxMapper.selectList(new LambdaQueryWrapper<EventOutbox>()
                .in(EventOutbox::getStatus, EventOutbox.STATUS_PENDING, EventOutbox.STATUS_FAILED)
                .orderByAsc(EventOutbox::getId).last("LIMIT " + batchSize));
        for (EventOutbox row : rows) send(row);
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
