package com.mware.community.biz.like.impl;

import com.mware.community.biz.like.LikeRedisStore;
import com.mware.community.biz.like.LikeService;
import com.mware.community.dto.response.LikeStatusResponse;
import org.springframework.stereotype.Service;

/**
 * 最终版点赞写路径：请求线程只访问 Redis，不同步访问 MySQL。
 * Lua 原子完成状态、计数、版本和 Stream Outbox；RabbitMQ 异步持久化 MySQL。
 */
@Service
public class LikeServiceImpl implements LikeService {
    private final LikeRedisStore likeRedisStore;

    public LikeServiceImpl(LikeRedisStore likeRedisStore) {
        this.likeRedisStore = likeRedisStore;
    }

    @Override
    public void like(Long postId, Long userId) {
        likeRedisStore.setLiked(postId, userId, true);
    }

    @Override
    public void unlike(Long postId, Long userId) {
        likeRedisStore.setLiked(postId, userId, false);
    }

    @Override
    public LikeStatusResponse likeStatus(Long postId, Long userId) {
        return LikeStatusResponse.builder()
                .postId(postId)
                .liked(likeRedisStore.isLiked(postId, userId))
                .likeCount(likeRedisStore.likeCount(postId))
                .build();
    }
}
