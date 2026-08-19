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
 * Redis Stream → RabbitMQ 可靠转发器。
 * <p>
 * Redis Stream 在这里充当 Outbox：用户点赞成功后事件先可靠落到 Redis，
 * 再由本组件定时转发到 RabbitMQ。只有 Broker Confirm=ACK 且 mandatory publish
 * 没有 ReturnedMessage 时才 XDEL；其他情况保留 Stream entry，下个周期继续重放。
 */
@Component
@Slf4j
public class LikeStreamRelay {

    private final StringRedisTemplate redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final LikeRedisStore likeRedisStore;

    @Value("${community.like.relay-batch-size:200}")
    private int batchSize;

    @Value("${community.like.confirm-timeout-ms:3000}")
    private long confirmTimeoutMs;

    @Value("${community.like.backlog-warn-size:100000}")
    private long backlogWarnSize;

    public LikeStreamRelay(StringRedisTemplate redisTemplate,
                           RabbitTemplate rabbitTemplate,
                           LikeRedisStore likeRedisStore) {
        this.redisTemplate = redisTemplate;
        this.rabbitTemplate = rabbitTemplate;
        this.likeRedisStore = likeRedisStore;
    }

    @Scheduled(fixedDelayString = "${community.like.relay-interval-ms:200}")
    public void relay() {
        // 扫描所有点赞事件分区。
        // eventStreamKeys() 本身通过 IntStream 生成分区 Key；这里继续使用 Stream 顺序遍历，
        // 不使用 parallelStream，避免一次调度并发打出过多 RabbitMQ Publisher Confirm。
        likeRedisStore.eventStreamKeys().stream()
                .forEach(this::relayPartition);
    }

    /**
     * 转发一个 Redis Stream 分区中的待发送事件。
     */
    private void relayPartition(String streamKey) {
        //   1. 先检查当前 Stream 积压量，超过告警阈值时记录日志，但不直接删除未发送事件。
        Long size = redisTemplate.opsForStream().size(streamKey);
        if (size != null && size > backlogWarnSize) {
            log.warn("like stream backlog: key={}, size={}", streamKey, size);
        }

        //   2. 每次只读取最老的一批事件，限制单次调度处理量，避免一次性拉取整个 Stream。
        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                .range(streamKey, Range.unbounded(), Limit.limit().count(batchSize));
        if (records == null || records.isEmpty()) {
            return;
        }

        //   3. 这里保留普通 for，而不是强行改 Stream：
        //      任意一条消息发送失败后必须立即 break，保证后面的记录暂不越过失败记录继续发送。
        for (MapRecord<String, Object, Object> record : records) {
            try {
                // Redis Stream MapRecord → LikeEvent
                LikeEvent event = likeRedisStore.toEvent(record);
                CorrelationData correlationData = new CorrelationData(event.getEventId());

                //   4. 发布到 Fanout Exchange；CorrelationData 用 eventId 关联 Publisher Confirm。
                rabbitTemplate.convertAndSend(
                        RabbitLikeConfig.EXCHANGE_LIKE,
                        "",
                        event,
                        correlationData);

                //   5. 等待 Broker Confirm；ACK 只能证明 Broker 接收，还要同时确认消息没有被 mandatory return。
                CorrelationData.Confirm confirm = correlationData.getFuture()
                        .get(confirmTimeoutMs, TimeUnit.MILLISECONDS);

                boolean brokerAck = confirm != null && confirm.isAck();
                boolean routed = correlationData.getReturned() == null;

                if (brokerAck && routed) {
                    //   6. RabbitMQ 已可靠接收并成功路由后，才删除 Redis Stream 中的 Outbox 事件。
                    redisTemplate.opsForStream().delete(streamKey, record.getId());
                    continue;
                }

                // 未确认 / 未路由：保留当前及后续事件，下个调度周期从这里继续重放。
                log.warn(
                        "like event not confirmed/routed, keep for replay: stream={}, id={}, ack={}, returned={}",
                        streamKey,
                        record.getId(),
                        brokerAck,
                        correlationData.getReturned() != null);
                break;
            } catch (Exception e) {
                // 异常时绝不 XDEL，否则 Redis 状态已经改变而持久化事件可能永久丢失。
                log.error("relay like event failed: stream={}, id={}", streamKey, record.getId(), e);
                break;
            }
        }
    }
}
