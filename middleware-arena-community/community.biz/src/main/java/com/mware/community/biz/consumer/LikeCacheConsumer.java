package com.mware.community.biz.consumer;

import com.mware.community.biz.config.RabbitLikeConfig;
import com.mware.community.dto.message.LikeEvent;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 缓存队列消费者（like.cache.queue）→ Redis 点赞状态同步。
 * <p>
 * 职责：把点赞事实同步到 Redis 读路径缓存，让 likeStatus 不落库即知"是否已赞 / 赞数"。
 * 手动 ACK + 消费者幂等 + 重试/DLQ 与 {@link LikeCountConsumer} 一致。
 */
@Component
@Slf4j
public class LikeCacheConsumer {

    private final ConsumerIdempotency idempotency;

    public LikeCacheConsumer(ConsumerIdempotency idempotency) {
        this.idempotency = idempotency;
    }

    @RabbitListener(queues = RabbitLikeConfig.QUEUE_CACHE, ackMode = "MANUAL")
    public void onLike(LikeEvent event, Channel channel,
                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        if (!idempotency.markConsumed(event.getEventId(), ConsumerIdempotency.CONSUMER_CACHE)) {
            channel.basicAck(deliveryTag, false);
            return;
        }
        // TODO[社区]：回写 Redis 点赞状态（两种方案选一，可对比压测）
        //   Set 方案：LIKE → SADD like:users:{postId} userId；UNLIKE → SREM
        //             读判赞：SISMEMBER；集合可做"谁赞过"分页，内存占用随赞数增长
        //   Bitmap 方案：LIKE → SETBIT like:bitmap:{postId} userId 1；UNLIKE → SETBIT 0
        //             读判赞：GETBIT；内存固定（每帖子 userId 位图），适合海量赞；集合语义弱
        //   同时维护计数缓存：INCRBY like:count:{postId} ±1（或由 count 消费者聚合后写入）
        channel.basicAck(deliveryTag, false);
    }
}
