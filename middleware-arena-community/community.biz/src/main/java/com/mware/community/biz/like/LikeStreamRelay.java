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

/**
 * Redis Stream Outbox -> RabbitMQ。
 * <p>
 * 只有 Broker Confirm=ACK 且 mandatory publish 没有 ReturnedMessage 时才 XDEL；
 * 其他情况保留 Stream entry 下个周期重放，因此整体是 at-least-once。
 */
@Component
@Slf4j
public class LikeStreamRelay {
    private final StringRedisTemplate redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final LikeRedisStore likeRedisStore;
    @Value("${community.like.relay-batch-size:200}") private int batchSize;
    @Value("${community.like.confirm-timeout-ms:3000}") private long confirmTimeoutMs;
    @Value("${community.like.backlog-warn-size:100000}") private long backlogWarnSize;

    public LikeStreamRelay(StringRedisTemplate redisTemplate,
                           RabbitTemplate rabbitTemplate,
                           LikeRedisStore likeRedisStore) {
        this.redisTemplate = redisTemplate;
        this.rabbitTemplate = rabbitTemplate;
        this.likeRedisStore = likeRedisStore;
    }

    @Scheduled(fixedDelayString = "${community.like.relay-interval-ms:200}")
    public void relay() {
        for (String streamKey : likeRedisStore.eventStreamKeys()) {
            relayPartition(streamKey);
        }
    }

    private void relayPartition(String streamKey) {
        Long size = redisTemplate.opsForStream().size(streamKey);
        if (size != null && size > backlogWarnSize) {
            log.warn("like stream backlog: key={}, size={}", streamKey, size);
        }

        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                .range(streamKey, Range.unbounded(), Limit.limit().count(batchSize));
        if (records == null || records.isEmpty()) {
            return;
        }

        for (MapRecord<String, Object, Object> record : records) {
            try {
                LikeEvent event = likeRedisStore.toEvent(record);
                CorrelationData correlationData = new CorrelationData(event.getEventId());
                rabbitTemplate.convertAndSend(
                        RabbitLikeConfig.EXCHANGE_LIKE,
                        "",
                        event,
                        correlationData);

                CorrelationData.Confirm confirm = correlationData.getFuture()
                        .get(confirmTimeoutMs, TimeUnit.MILLISECONDS);

                boolean brokerAck = confirm != null && confirm.isAck();
                boolean routed = correlationData.getReturned() == null;
                if (brokerAck && routed) {
                    redisTemplate.opsForStream().delete(streamKey, record.getId());
                    continue;
                }

                log.warn(
                        "like event not confirmed/routed, keep for replay: stream={}, id={}, ack={}, returned={}",
                        streamKey,
                        record.getId(),
                        brokerAck,
                        correlationData.getReturned() != null);
                break;
            } catch (Exception e) {
                log.error("relay like event failed: stream={}, id={}", streamKey, record.getId(), e);
                // 不删除。下个调度周期从最老记录继续重放。
                break;
            }
        }
    }
}
