package com.mware.community.biz.like;

import com.mware.community.dto.response.LikeStatusResponse;

/**
 * 点赞业务接口（面向接口编程）。
 * <p>
 * 对外只暴露 DTO，domain 实体仅存在于实现内部。实现见 {@code impl/LikeServiceImpl}。
 */
public interface LikeService {

    /** 点赞 / 取消点赞（事务性 Outbox：post_like 事实 + event_outbox 事件同事务双写，异步聚合计数） */
    void like(Long postId, Long userId);

    /** 点赞状态（liked 读 Redis 集合 / Bitmap，likeCount 读 Redis 缓存，最终一致） */
    LikeStatusResponse likeStatus(Long postId);
}
