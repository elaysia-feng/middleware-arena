package com.mware.community.controller;

import com.mware.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 社区接口（骨架占位，返回统一 {@link ApiResponse}）。
 * <p>
 * TODO：
 *   1. POST /community/post 发帖（CRUD）
 *   2. POST /community/comment 评论
 *   3. POST /community/like 点赞
 *   4. POST /community/favorite 收藏
 *   5. POST /community/follow 关注
 *   6. GET  /community/search ES 全文搜索
 *   7. GET  /community/feed 信息流（关注 + 推荐）
 */
@Tag(name = "社区")
@RestController
@RequestMapping("/community")
public class CommunityController {

    @Operation(summary = "健康检查")
    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        return ApiResponse.ok("pong");
    }
}
