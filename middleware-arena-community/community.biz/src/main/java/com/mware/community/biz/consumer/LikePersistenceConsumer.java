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

/**
 * 点赞事件持久化消费者：RabbitMQ → ShardingSphere → MySQL。
 * <p>
 * MQ 使用 at-least-once 投递，因此消息可能重复或乱序；
 * post_like 状态和 community_post.like_count 都通过 version 条件更新保证最终状态不会被旧事件覆盖。
 */
@Component
public class LikePersistenceConsumer {

    private final PostLikeMapper postLikeMapper;
    private final CommunityPostMapper communityPostMapper;

    public LikePersistenceConsumer(PostLikeMapper postLikeMapper,
                                   CommunityPostMapper communityPostMapper) {
        this.postLikeMapper = postLikeMapper;
        this.communityPostMapper = communityPostMapper;
    }

    @RabbitListener(queues = RabbitLikeConfig.QUEUE_PERSIST, ackMode = "MANUAL")
    public void onLike(LikeEvent event,
                       Channel channel,
                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        //   1. 按 postId 经过 ShardingSphere 路由到对应 post_like 物理分片。
        //      upsertState 内部比较 version，重复事件或旧事件不会覆盖更新状态。
        postLikeMapper.upsertState(
                event.getPostId(),
                event.getUserId(),
                Boolean.TRUE.equals(event.getLiked()),
                event.getVersion());

        //   2. 将 Redis 中事件携带的最新 likeCount 异步同步到帖子表。
        //      同样要求 event.version 更新，避免 v101 晚于 v102 到达时把计数回滚。
        communityPostMapper.updateLikeCountIfNewer(
                event.getPostId(),
                event.getLikeCount(),
                event.getVersion());

        //   3. 两个持久化步骤都执行成功后才 ACK；抛异常时交给 Rabbit Retry / DLQ 处理。
        channel.basicAck(deliveryTag, false);
    }
}
