package com.mware.community.dto.request;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发表评论请求对象（纯 DTO，前端输入）。
 * <p>
 * - postId / authorId 不由客户端传：postId 以路径为准，authorId 从
 *   {@link com.mware.common.web.UserContext} 注入，防伪造。
 * - parentId：为空 = 一级评论，非空 = 回复（Service 层强制校验同帖，禁止跨帖回复）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommentRequest {

    private String content;

    /** 父评论 ID；为空 = 一级评论，非空 = 回复（必须挂在同一帖子下，由 Service 层校验） */
    private Long parentId;
}