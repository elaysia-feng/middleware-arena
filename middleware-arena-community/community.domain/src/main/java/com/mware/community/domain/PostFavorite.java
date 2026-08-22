package com.mware.community.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 收藏事实实体（@TableName = post_favorite）。
 * <p>
 * post_id + user_id 唯一；favorited=false tombstone + version 防旧消息复活。
 * 收藏数写入 {@code community_post.favorite_count}（同点赞链路，异步聚合，最终一致）。
 */
@Data
@TableName("post_favorite")
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostFavorite {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 帖子 ID */
    private Long postId;

    /** 收藏用户 ID */
    private Long userId;

    private Boolean favorited;

    private Long version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
