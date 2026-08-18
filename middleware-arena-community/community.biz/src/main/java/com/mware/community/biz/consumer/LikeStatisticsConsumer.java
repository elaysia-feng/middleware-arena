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

/** 热度统计：eventId 去重 + 分区日榜 ZINCRBY 在 Lua 中原子完成。 */
@Component
public class LikeStatisticsConsumer {
    private final LikeRedisStore likeRedisStore;
    public LikeStatisticsConsumer(LikeRedisStore likeRedisStore) { this.likeRedisStore = likeRedisStore; }

    @RabbitListener(queues = RabbitLikeConfig.QUEUE_STATISTICS, ackMode = "MANUAL")
    public void onLike(LikeEvent event, Channel channel,
                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        likeRedisStore.applyStatistics(event);
        channel.basicAck(deliveryTag, false);
    }
}
