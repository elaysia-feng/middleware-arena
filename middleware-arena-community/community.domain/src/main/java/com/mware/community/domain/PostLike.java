package com.mware.community.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 点赞最终事实状态；liked=false tombstone + version 防旧消息复活。 */
@Data
@TableName("post_like")
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostLike {
    private Long postId;
    private Long userId;
    private Boolean liked;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
