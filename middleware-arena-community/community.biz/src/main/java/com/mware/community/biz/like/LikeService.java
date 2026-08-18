package com.mware.community.biz.like;

import com.mware.community.dto.response.LikeStatusResponse;

/** 点赞业务接口：显式目标状态，避免 toggle 在 HTTP 重试时反向。 */
public interface LikeService {
    void like(Long postId, Long userId);
    void unlike(Long postId, Long userId);
    LikeStatusResponse likeStatus(Long postId, Long userId);
}
