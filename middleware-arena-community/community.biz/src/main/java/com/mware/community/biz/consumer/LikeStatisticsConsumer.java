package com.mware.community.biz.consumer;

import com.mware.community.biz.config.RabbitLikeConfig;
import com.mware.community.biz.like.LikeRedisStore;
import com.mware.community.dto.message.LikeEvent;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 点赞热度统计消费者。
 * <p>
 * 与 MySQL 持久化消费者解耦：同一条 LikeEvent 由 Fanout Exchange 分发到独立队列，
 * 本消费者只负责 eventId 去重和当日热榜 ZSet 增减，不参与点赞事实表持久化。
 */
@Component
public class LikeStatisticsConsumer {

    private final LikeRedisStore likeRedisStore;

    public LikeStatisticsConsumer(LikeRedisStore likeRedisStore) {
        this.likeRedisStore = likeRedisStore;
    }

    @RabbitListener(queues = RabbitLikeConfig.QUEUE_STATISTICS, ackMode = "MANUAL")
    public void onLike(LikeEvent event,
                       Channel channel,
                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        //   1. Lua 用 eventId 做消费幂等，再通过 ZINCRBY 更新当天热榜。
        //      LIKE delta=+1，UNLIKE delta=-1；重复 MQ 消息不会重复累计。
        likeRedisStore.applyStatistics(event);

        //   2. Redis 统计成功后手动 ACK；异常时不 ACK，交给 Retry / DLQ 处理。
        channel.basicAck(deliveryTag, false);
    }
}
