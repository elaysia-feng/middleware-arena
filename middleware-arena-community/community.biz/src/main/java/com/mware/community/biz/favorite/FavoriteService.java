package com.mware.community.biz.favorite;

/**
 * 收藏业务接口（面向接口编程）。
 * <p>
 * 对外只暴露 DTO，domain 实体仅存在于实现内部。实现见 {@code impl/FavoriteServiceImpl}。
 */
public interface FavoriteService {

    /** 收藏 / 取消收藏 */
    void favorite(Long postId, Long userId);
}
