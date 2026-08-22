package com.mware.community.biz.favorite.impl;

import com.mware.community.biz.favorite.FavoriteService;
import com.mware.community.biz.favorite.FavoriteRedisStore;
import com.mware.community.dto.response.FavoriteStatusResponse;
import org.springframework.stereotype.Service;

/**
 * 收藏业务实现。请求链路只写 Redis，MySQL 由 Stream → RabbitMQ 异步持久化。
 */
@Service
public class FavoriteServiceImpl implements FavoriteService {

    private final FavoriteRedisStore favoriteRedisStore;

    public FavoriteServiceImpl(FavoriteRedisStore favoriteRedisStore) {
        this.favoriteRedisStore = favoriteRedisStore;
    }

    @Override
    public void favorite(Long postId, Long userId) {
        favoriteRedisStore.setFavorited(postId, userId, true);
    }

    @Override
    public void unfavorite(Long postId, Long userId) {
        favoriteRedisStore.setFavorited(postId, userId, false);
    }

    @Override
    public FavoriteStatusResponse favoriteStatus(Long postId, Long userId) {
        return FavoriteStatusResponse.builder()
                .postId(postId)
                .favorited(favoriteRedisStore.isFavorited(postId, userId))
                .favoriteCount(favoriteRedisStore.favoriteCount(postId))
                .build();
    }
}
