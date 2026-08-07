package com.mware.community.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 帖子响应对象（纯 DTO，后端输出，对齐 {@code community.domain.CommunityPost}）。
 * <p>
 * likeCount / favoriteCount / commentCount 为聚合计数，当前骨架阶段由 Controller 置 0，
 * 待接入 Redis 计数 / DB 聚合后从服务侧填充。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostResponse {

    private Long id;

    private String title;

    private String content;

    private Long authorId;

    private Long likeCount;

    private Long favoriteCount;

    private Long commentCount;

    private LocalDateTime createdAt;
}
