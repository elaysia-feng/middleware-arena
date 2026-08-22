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
 * 社区帖子聚合实体。
 * <p>
 * 点赞数和收藏数由 Redis Outbox 事件异步回写；对应 version 用于拒绝乱序旧消息，
 * 因此计数与关系事实是最终一致，而不是请求内强一致。
 */
@Data
@TableName("community_post")
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityPost {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String content;
    private Long authorId;
    private Long likeCount;
    /** 最近已持久化的帖子点赞事件版本。 */
    private Long likeVersion;
    private Long favoriteCount;
    /** 最近已持久化的帖子收藏事件版本。 */
    private Long favoriteVersion;
    private Long commentCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
