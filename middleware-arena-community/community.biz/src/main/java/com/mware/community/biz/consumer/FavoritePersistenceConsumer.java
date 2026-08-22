package com.mware.community.biz.consumer;

import com.mware.community.biz.config.RabbitFavoriteConfig;
import com.mware.community.dto.message.FavoriteEvent;
import com.mware.community.mapper.CommunityPostMapper;
import com.mware.community.mapper.PostFavoriteMapper;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 收藏事件持久化消费者。
 * <p>
 * RabbitMQ 为至少一次投递，消息可能重复或乱序；事实表和帖子聚合计数都使用 version
 * 条件更新，只有更大的版本才能覆盖旧状态。两步成功后才手动 ACK。
 */
@Component
public class FavoritePersistenceConsumer {
    private final PostFavoriteMapper postFavoriteMapper;
    private final CommunityPostMapper communityPostMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    public FavoritePersistenceConsumer(PostFavoriteMapper postFavoriteMapper,
                                       CommunityPostMapper communityPostMapper,
                                       RedisTemplate<String, Object> redisTemplate) {
        this.postFavoriteMapper = postFavoriteMapper;
        this.communityPostMapper = communityPostMapper;
        this.redisTemplate = redisTemplate;
    }

    @RabbitListener(queues = RabbitFavoriteConfig.QUEUE_PERSIST, ackMode = "MANUAL")
    public void onFavorite(FavoriteEvent event,
                           Channel channel,
                           @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        // 先保存用户收藏事实，再更新帖子聚合计数；旧 version 到达时两条 SQL 都不会回滚状态。
        postFavoriteMapper.upsertState(
                event.getPostId(), event.getUserId(), Boolean.TRUE.equals(event.getFavorited()), event.getVersion());
        communityPostMapper.updateFavoriteCountIfNewer(
                event.getPostId(), event.getFavoriteCount(), event.getVersion());
        // 聚合计数已变化，删除帖子详情缓存，下一次读取回源最新 MySQL 数据。
        redisTemplate.delete("community:post:" + event.getPostId());

        // Mapper 和缓存处理均未抛异常后确认消息；异常交给统一 Retry/DLQ。
        channel.basicAck(deliveryTag, false);
    }
}
