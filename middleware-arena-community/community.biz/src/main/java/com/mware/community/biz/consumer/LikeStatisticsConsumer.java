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
 * 统计队列消费者（like.statistics.queue）→ 数据分析 / 热门帖子。
 * <p>
 * 与 count / cache 互不干扰，演示 RabbitMQ fanout 一投多取的消费隔离。
 * 手动 ACK + 消费者幂等 + 重试/DLQ 与 {@link LikeCountConsumer} 一致。
 */
@Component
@Slf4j
public class LikeStatisticsConsumer {

    private final ConsumerIdempotency idempotency;

    public LikeStatisticsConsumer(ConsumerIdempotency idempotency) {
        this.idempotency = idempotency;
    }

    @RabbitListener(queues = RabbitLikeConfig.QUEUE_STATISTICS, ackMode = "MANUAL")
    public void onLike(LikeEvent event, Channel channel,
                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        if (!idempotency.markConsumed(event.getEventId(), ConsumerIdempotency.CONSUMER_STATISTICS)) {
            channel.basicAck(deliveryTag, false);
            return;
        }
        // TODO[社区]：统计 / 数据分析
        //   1. 当日热门：ZINCRBY like:hotpost:{yyyyMMdd} ±1 postId（ZRevRange 取 TOP N）
        //   2. 实验链路：与 experiment-service 联动，点赞行为可作为压测实验的观测数据
        //   3. 可扩展：埋点 / 用户行为画像（慢消费者，与 count/cache 隔离互不影响）
        channel.basicAck(deliveryTag, false);
    }
}
