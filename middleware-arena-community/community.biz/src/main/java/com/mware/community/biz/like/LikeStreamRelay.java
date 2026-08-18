package com.mware.community.biz.like;

import com.mware.community.biz.config.RabbitLikeConfig;
import com.mware.community.dto.message.LikeEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** Stream Outbox -> RabbitMQ；Confirm 成功后 XDEL，失败保留并重放（at-least-once）。 */
@Component
@Slf4j
public class LikeStreamRelay {
    private final StringRedisTemplate redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final LikeRedisStore likeRedisStore;
    @Value("${community.like.relay-batch-size:200}") private int batchSize;
    @Value("${community.like.confirm-timeout-ms:3000}") private long confirmTimeoutMs;
    @Value("${community.like.backlog-warn-size:100000}") private long backlogWarnSize;

    public LikeStreamRelay(StringRedisTemplate redisTemplate, RabbitTemplate rabbitTemplate,
                           LikeRedisStore likeRedisStore) {
        this.redisTemplate = redisTemplate;
        this.rabbitTemplate = rabbitTemplate;
        this.likeRedisStore = likeRedisStore;
    }

    @Scheduled(fixedDelayString = "${community.like.relay-interval-ms:200}")
    public void relay() {
        for (String streamKey : likeRedisStore.eventStreamKeys()) relayPartition(streamKey);
    }

    private void relayPartition(String streamKey) {
        Long size = redisTemplate.opsForStream().size(streamKey);
        if (size != null && size > backlogWarnSize) log.warn("like stream backlog: key={}, size={}", streamKey, size);
        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                .range(streamKey, Range.unbounded(), Limit.limit().count(batchSize));
        if (records == null || records.isEmpty()) return;
        for (MapRecord<String, Object, Object> record : records) {
            try {
                LikeEvent event = likeRedisStore.toEvent(record);
                CorrelationData correlationData = new CorrelationData(event.getEventId());
                rabbitTemplate.convertAndSend(RabbitLikeConfig.EXCHANGE_LIKE, "", event, correlationData);
                CorrelationData.Confirm confirm = correlationData.getFuture()
                        .get(confirmTimeoutMs, TimeUnit.MILLISECONDS);
                if (confirm != null && confirm.isAck()) {
                    redisTemplate.opsForStream().delete(streamKey, record.getId());
                } else {
                    log.warn("publisher confirm failed: stream={}, id={}", streamKey, record.getId());
                    break;
                }
            } catch (Exception e) {
                log.error("relay like event failed: stream={}, id={}", streamKey, record.getId(), e);
                break;
            }
        }
    }
}
