package com.mware.community.biz.favorite;

import com.mware.community.dto.response.FavoriteStatusResponse;

/**
 * 收藏业务接口（面向接口编程）。
 * <p>
 * 对外只暴露 DTO，domain 实体仅存在于实现内部。实现见 {@code impl/FavoriteServiceImpl}。
 */
public interface FavoriteService {

    /** 收藏（幂等）。 */
    void favorite(Long postId, Long userId);

    /** 取消收藏（幂等）。 */
    void unfavorite(Long postId, Long userId);

    /** 查询当前用户的收藏状态与实时收藏数。 */
    FavoriteStatusResponse favoriteStatus(Long postId, Long userId);
}
