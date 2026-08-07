package com.mware.community.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 评论响应对象（纯 DTO，后端输出，对齐 {@code community.domain.Comment}）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommentResponse {

    private Long id;

    private Long postId;

    private Long authorId;

    private String content;

    private LocalDateTime createdAt;
}
