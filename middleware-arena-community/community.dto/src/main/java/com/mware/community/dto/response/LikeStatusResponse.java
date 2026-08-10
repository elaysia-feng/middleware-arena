package com.mware.community.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 点赞状态响应（GET /post/{postId}/like/status）。
 * <p>
 * liked 读 Redis（cache 消费者写入的 like:users:{postId} 集合 / Bitmap）；
 * likeCount 读 Redis 缓存，未命中降级读 community_post.like_count。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LikeStatusResponse {

    private Long postId;

    /** 当前用户是否已点赞 */
    private Boolean liked;

    /** 点赞数（最终一致） */
    private Long likeCount;
}
