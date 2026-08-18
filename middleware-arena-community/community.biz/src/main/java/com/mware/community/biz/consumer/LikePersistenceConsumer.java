package com.mware.community.biz.consumer;

import com.mware.community.biz.config.RabbitLikeConfig;
import com.mware.community.dto.message.LikeEvent;
import com.mware.community.mapper.CommunityPostMapper;
import com.mware.community.mapper.PostLikeMapper;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** RabbitMQ -> 分片 MySQL；两个写操作都由 version 保证重复/乱序幂等。 */
@Component
public class LikePersistenceConsumer {
    private final PostLikeMapper postLikeMapper;
    private final CommunityPostMapper communityPostMapper;

    public LikePersistenceConsumer(PostLikeMapper postLikeMapper, CommunityPostMapper communityPostMapper) {
        this.postLikeMapper = postLikeMapper;
        this.communityPostMapper = communityPostMapper;
    }

    @RabbitListener(queues = RabbitLikeConfig.QUEUE_PERSIST, ackMode = "MANUAL")
    public void onLike(LikeEvent event, Channel channel,
                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        postLikeMapper.upsertState(event.getPostId(), event.getUserId(),
                Boolean.TRUE.equals(event.getLiked()), event.getVersion());
        communityPostMapper.updateLikeCountIfNewer(event.getPostId(), event.getLikeCount(), event.getVersion());
        channel.basicAck(deliveryTag, false);
    }
}
