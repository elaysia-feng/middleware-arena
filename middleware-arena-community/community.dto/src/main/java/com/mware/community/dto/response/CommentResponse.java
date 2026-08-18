package com.mware.community.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 评论响应对象（纯 DTO，后端输出，对齐 {@code community.domain.Comment}）。
 * <p>
 * parentId：为空 = 一级评论，非空 = 回复（前端用于楼中楼折叠 / 回复链路展示）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommentResponse {

    private Long id;

    private Long postId;

    private Long authorId;

    /** 父评论 ID；空 = 一级评论，非空 = 回复 */
    private Long parentId;

    private String content;

    private LocalDateTime createdAt;
}