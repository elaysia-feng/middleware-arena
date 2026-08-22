package com.mware.community.biz.favorite;

import com.mware.community.biz.config.RabbitFavoriteConfig;
import com.mware.community.dto.message.FavoriteEvent;
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
 * Redis Stream 收藏 Outbox 到 RabbitMQ 的可靠转发器。
 * <p>
 * 只在 Publisher Confirm=ACK 且消息成功路由后删除 Stream entry；
 * 超时、NACK、Return 或转换异常均保留原事件，下一次调度继续重放。
 */
@Component
@Slf4j
public class FavoriteStreamRelay {

    private final StringRedisTemplate redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final FavoriteRedisStore favoriteRedisStore;

    @Value("${community.favorite.relay-batch-size:200}")
    private int batchSize;

    @Value("${community.favorite.confirm-timeout-ms:3000}")
    private long confirmTimeoutMs;

    public FavoriteStreamRelay(StringRedisTemplate redisTemplate,
                               RabbitTemplate rabbitTemplate,
                               FavoriteRedisStore favoriteRedisStore) {
        this.redisTemplate = redisTemplate;
        this.rabbitTemplate = rabbitTemplate;
        this.favoriteRedisStore = favoriteRedisStore;
    }

    @Scheduled(fixedDelayString = "${community.favorite.relay-interval-ms:200}")
    public void relay() {
        // 顺序扫描分区，避免一次调度并发等待大量 Publisher Confirm。
        favoriteRedisStore.eventStreamKeys().forEach(this::relayPartition);
    }

    /** 转发一个分区内最早的一批事件；单条失败后停止越过，保持重放顺序。 */
    private void relayPartition(String streamKey) {
        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                .range(streamKey, Range.unbounded(), Limit.limit().count(batchSize));
        if (records == null || records.isEmpty()) {
            return;
        }

        for (MapRecord<String, Object, Object> record : records) {
            try {
                FavoriteEvent event = favoriteRedisStore.toEvent(record);
                CorrelationData correlationData = new CorrelationData(event.getEventId());
                rabbitTemplate.convertAndSend(RabbitFavoriteConfig.EXCHANGE_FAVORITE, "", event, correlationData);

                // ACK 只表示 Broker 接收；ReturnedMessage 非空仍代表没有路由到业务队列。
                CorrelationData.Confirm confirm = correlationData.getFuture()
                        .get(confirmTimeoutMs, TimeUnit.MILLISECONDS);
                if (confirm != null && confirm.isAck() && correlationData.getReturned() == null) {
                    redisTemplate.opsForStream().delete(streamKey, record.getId());
                    continue;
                }
                log.warn("favorite event not confirmed, keep for replay: stream={}, id={}", streamKey, record.getId());
                break;
            } catch (Exception e) {
                // 失败时绝不 XDEL，否则 Redis 状态已经改变但持久化事件会永久丢失。
                log.error("relay favorite event failed: stream={}, id={}", streamKey, record.getId(), e);
                break;
            }
        }
    }
}
