package com.mware.community.biz.favorite.impl;

import com.mware.community.biz.favorite.FavoriteService;
import com.mware.community.mapper.PostFavoriteMapper;
import org.springframework.stereotype.Service;

/**
 * 收藏业务实现（骨架占位）。
 * <p>
 * TODO[社区]：接入 community.mapper 后实现。
 */
@Service
public class FavoriteServiceImpl implements FavoriteService {

    private final PostFavoriteMapper postFavoriteMapper;

    public FavoriteServiceImpl(PostFavoriteMapper postFavoriteMapper) {
        this.postFavoriteMapper = postFavoriteMapper;
    }

    @Override
    public void favorite(Long postId, Long userId) {
        // TODO[社区]：收藏 / 取消收藏（表 post_favorite，post_id + user_id 唯一）
        //   1. postFavoriteMapper.selectCount(eq post_id, eq user_id) 判定是否已收藏
        //   2. 不存在：insert（收藏）
        //   3. 存在：deleteById（取消收藏）
        //   4. 收藏数同点赞链路走 outbox + MQ 异步聚合（eventType=FAVORITE/UNFAVORITE）
    }
}
