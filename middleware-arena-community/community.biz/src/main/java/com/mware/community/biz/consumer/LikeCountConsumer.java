package com.mware.community.biz.consumer;

import com.mware.community.biz.config.RabbitLikeConfig;
import com.mware.community.biz.count.LikePendingCounter;
import com.mware.community.dto.message.LikeEvent;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 计数队列消费者（like.count.queue）→ 点赞数异步聚合。
 * <p>
 * 关键：<b>不逐条 UPDATE 数据库</b>（写放大）。先 HINCRBY 累加到 Redis 待刷增量
 * {@code like:counter:pending}，由 {@link LikePendingCounter} 批量刷入 community_post.like_count。
 * <p>
 * 消费可靠性（MANUAL ACK + 重试 + DLQ）：
 * <ul>
 *   <li>成功 → basicAck（手动确认，确认前宕机消息重派）</li>
 *   <li>重复投递 → consumer_event 唯一键冲突（ConsumerIdempotency），ACK 跳过</li>
 *   <li>业务异常 → 不 ACK、异常向上抛 → spring.rabbitmq.listener.simple.retry 重试（默认 3 次）→
 *       耗尽后 defaultRequeueRejected=false → 进 x-dead-letter-exchange → like.dlq</li>
 * </ul>
 */
@Component
@Slf4j
public class LikeCountConsumer {

    private final ConsumerIdempotency idempotency;
    private final LikePendingCounter pendingCounter;

    public LikeCountConsumer(ConsumerIdempotency idempotency, LikePendingCounter pendingCounter) {
        this.idempotency = idempotency;
        this.pendingCounter = pendingCounter;
    }

    @RabbitListener(queues = RabbitLikeConfig.QUEUE_COUNT, ackMode = "MANUAL")
    public void onLike(LikeEvent event, Channel channel,
                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        if (!idempotency.markConsumed(event.getEventId(), ConsumerIdempotency.CONSUMER_COUNT)) {
            channel.basicAck(deliveryTag, false); // 重复投递，已聚合过，ACK 跳过
            return;
        }
        int delta = LikeEvent.ACTION_LIKE.equals(event.getAction()) ? 1 : -1;
        pendingCounter.increment(event.getPostId(), delta);
        channel.basicAck(deliveryTag, false);
    }
}
