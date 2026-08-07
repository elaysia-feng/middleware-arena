package com.mware.community.controller;

import com.mware.common.web.ApiException;
import com.mware.common.web.ApiResponse;
import com.mware.common.web.ErrorCode;
import com.mware.common.web.UserContext;
import com.mware.community.biz.CommunityService;
import com.mware.community.dto.request.CommentRequest;
import com.mware.community.dto.request.CreatePostRequest;
import com.mware.community.dto.response.CommentResponse;
import com.mware.community.dto.response.PostResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 社区接口（薄层，业务与 domain↔dto 映射全部下沉到 {@link CommunityService}）。
 * <p>
 * TODO：业务逻辑待接入 community.mapper / Redis / ES 后补齐。
 */
@Tag(name = "社区")
@RestController
@RequestMapping("/community")
public class CommunityController {

    private final CommunityService communityService;

    public CommunityController(CommunityService communityService) {
        this.communityService = communityService;
    }

    @Operation(summary = "健康检查")
    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        return ApiResponse.ok("pong");
    }

    @Operation(summary = "发布帖子")
    @PostMapping("/post/create")
    public ApiResponse<PostResponse> createPost(@RequestBody CreatePostRequest request) {
        return ApiResponse.ok(communityService.createPost(request));
    }

    @Operation(summary = "编辑帖子")
    @PutMapping("/post/{postId}")
    public ApiResponse<PostResponse> updatePost(@PathVariable Long postId,
                                                @RequestBody CreatePostRequest request) {
        return ApiResponse.ok(communityService.updatePost(postId, request));
    }

    @Operation(summary = "删除帖子")
    @DeleteMapping("/post/{postId}")
    public ApiResponse<Void> deletePost(@PathVariable Long postId) {
        communityService.deletePost(postId);
        return ApiResponse.ok();
    }

    @Operation(summary = "帖子详情")
    @GetMapping("/post/{postId}")
    public ApiResponse<PostResponse> getPost(@PathVariable Long postId) {
        return ApiResponse.ok(communityService.getPost(postId));
    }

    @Operation(summary = "帖子分页列表")
    @GetMapping("/post/page")
    public ApiResponse<List<PostResponse>> pagePosts(@RequestParam int page, @RequestParam int size) {
        return ApiResponse.ok(communityService.pagePosts(page, size));
    }

    @Operation(summary = "发表评论")
    @PostMapping("/post/{postId}/comment")
    public ApiResponse<CommentResponse> addComment(@PathVariable Long postId,
                                                   @RequestBody CommentRequest request) {
        return ApiResponse.ok(communityService.addComment(postId, request));
    }

    @Operation(summary = "帖子评论分页")
    @GetMapping("/post/{postId}/comment/page")
    public ApiResponse<List<CommentResponse>> pageComments(@PathVariable Long postId,
                                                           @RequestParam int page,
                                                           @RequestParam int size) {
        return ApiResponse.ok(communityService.pageComments(postId, page, size));
    }

    @Operation(summary = "点赞 / 取消点赞")
    @PostMapping("/post/{postId}/like")
    public ApiResponse<Void> like(@PathVariable Long postId) {
        communityService.like(postId, currentUserId());
        return ApiResponse.ok();
    }

    @Operation(summary = "收藏 / 取消收藏")
    @PostMapping("/post/{postId}/favorite")
    public ApiResponse<Void> favorite(@PathVariable Long postId) {
        communityService.favorite(postId, currentUserId());
        return ApiResponse.ok();
    }

    @Operation(summary = "关注 / 取消关注")
    @PostMapping("/user/{userId}/follow")
    public ApiResponse<Void> follow(@PathVariable Long userId) {
        // 当前登录用户关注 userId 指向的用户（follow 签名：authorId=被关注者，userId=关注者）
        communityService.follow(userId, currentUserId());
        return ApiResponse.ok();
    }

    @Operation(summary = "ES 全文搜索")
    @GetMapping("/search")
    public ApiResponse<List<PostResponse>> search(@RequestParam String keyword,
                                                  @RequestParam int page,
                                                  @RequestParam int size) {
        return ApiResponse.ok(communityService.search(keyword, page, size));
    }

    /**
     * 从 {@link UserContext} 取当前登录用户 ID；未登录（未过网关 / 直连服务端口）直接抛 401。
     */
    private Long currentUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
