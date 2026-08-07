package com.mware.community.dto.request;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发布 / 编辑帖子请求对象（纯 DTO，前端输入）。
 * <p>
 * authorId 不由客户端传，由 Controller 从 {@link com.mware.common.web.UserContext} 注入，防伪造。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreatePostRequest {

    private String title;

    private String content;

    /** 可选：帖子标签（当前未持久化，预留） */
    private String tags;
}
