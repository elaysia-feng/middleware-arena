package com.mware.community.controller;

import com.mware.common.web.ApiException;
import com.mware.common.web.ApiResponse;
import com.mware.common.web.ErrorCode;
import com.mware.common.web.UserContext;
import com.mware.community.biz.comment.CommentService;
import com.mware.community.biz.favorite.FavoriteService;
import com.mware.community.biz.follow.FollowService;
import com.mware.community.biz.like.LikeService;
import com.mware.community.biz.post.PostService;
import com.mware.community.biz.search.SearchService;
import com.mware.community.dto.request.CommentRequest;
import com.mware.community.dto.request.CreatePostRequest;
import com.mware.community.dto.response.CommentResponse;
import com.mware.community.dto.response.FavoriteStatusResponse;
import com.mware.community.dto.response.FollowStatusResponse;
import com.mware.community.dto.response.LikeStatusResponse;
import com.mware.community.dto.response.PostResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 社区 HTTP 接口入口。
 * <p>
 * Controller 只负责请求参数绑定、当前用户身份读取和统一响应封装；帖子、评论、点赞、
 * 收藏、关注及搜索的业务规则分别由对应 Service 处理。
 */
@Tag(name = "社区")
@RestController
@RequestMapping("/community")
public class CommunityController {
    private final PostService postService;
    private final CommentService commentService;
    private final LikeService likeService;
    private final FavoriteService favoriteService;
    private final FollowService followService;
    private final SearchService searchService;

    public CommunityController(PostService postService, CommentService commentService, LikeService likeService,
                               FavoriteService favoriteService, FollowService followService, SearchService searchService) {
        this.postService = postService;
        this.commentService = commentService;
        this.likeService = likeService;
        this.favoriteService = favoriteService;
        this.followService = followService;
        this.searchService = searchService;
    }

    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        return ApiResponse.ok("pong");
    }

    @PostMapping("/post/create")
    public ApiResponse<PostResponse> createPost(@RequestBody CreatePostRequest request) {
        return ApiResponse.ok(postService.createPost(request));
    }

    @PutMapping("/post/{postId}")
    public ApiResponse<PostResponse> updatePost(
            @PathVariable("postId") Long postId,
            @RequestBody CreatePostRequest request) {
        return ApiResponse.ok(postService.updatePost(postId, request));
    }

    @DeleteMapping("/post/{postId}")
    public ApiResponse<Void> deletePost(@PathVariable("postId") Long postId) {
        postService.deletePost(postId);
        return ApiResponse.ok();
    }

    @GetMapping("/post/{postId}")
    public ApiResponse<PostResponse> getPost(@PathVariable("postId") Long postId) {
        return ApiResponse.ok(postService.getPost(postId));
    }

    @GetMapping("/post/page")
    public ApiResponse<List<PostResponse>> pagePosts(
            @RequestParam("page") int page,
            @RequestParam("size") int size) {
        return ApiResponse.ok(postService.pagePosts(page, size));
    }

    @PostMapping("/post/{postId}/comment")
    public ApiResponse<CommentResponse> addComment(
            @PathVariable("postId") Long postId,
            @RequestBody CommentRequest request) {
        return ApiResponse.ok(commentService.addComment(postId, request));
    }

    @GetMapping("/post/{postId}/comment/page")
    public ApiResponse<List<CommentResponse>> pageComments(
            @PathVariable("postId") Long postId,
            @RequestParam("page") int page,
            @RequestParam("size") int size) {
        return ApiResponse.ok(commentService.pageComments(postId, page, size));
    }

    @GetMapping("/comment/{parentId}/reply/page")
    public ApiResponse<List<CommentResponse>> pageReplies(
            @PathVariable("parentId") Long parentId,
            @RequestParam("page") int page,
            @RequestParam("size") int size) {
        return ApiResponse.ok(commentService.pageReplies(parentId, page, size));
    }

    @DeleteMapping("/post/{postId}/comment/{commentId}")
    public ApiResponse<Void> deleteComment(
            @PathVariable("postId") Long postId,
            @PathVariable("commentId") Long commentId) {
        commentService.deleteComment(postId, commentId);
        return ApiResponse.ok();
    }

    @Operation(summary = "点赞（幂等 PUT）")
    @PutMapping("/post/{postId}/like")
    public ApiResponse<Void> like(@PathVariable("postId") Long postId) {
        likeService.like(postId, currentUserId());
        return ApiResponse.ok();
    }

    @Operation(summary = "取消点赞（幂等 DELETE）")
    @DeleteMapping("/post/{postId}/like")
    public ApiResponse<Void> unlike(@PathVariable("postId") Long postId) {
        likeService.unlike(postId, currentUserId());
        return ApiResponse.ok();
    }

    @GetMapping("/post/{postId}/like/status")
    public ApiResponse<LikeStatusResponse> likeStatus(@PathVariable("postId") Long postId) {
        return ApiResponse.ok(likeService.likeStatus(postId, currentUserId()));
    }

    @Operation(summary = "收藏（幂等 PUT，兼容旧 POST）")
    @RequestMapping(value = "/post/{postId}/favorite", method = {RequestMethod.PUT, RequestMethod.POST})
    public ApiResponse<Void> favorite(@PathVariable("postId") Long postId) {
        favoriteService.favorite(postId, currentUserId());
        return ApiResponse.ok();
    }

    @Operation(summary = "取消收藏（幂等 DELETE）")
    @DeleteMapping("/post/{postId}/favorite")
    public ApiResponse<Void> unfavorite(@PathVariable("postId") Long postId) {
        favoriteService.unfavorite(postId, currentUserId());
        return ApiResponse.ok();
    }

    @GetMapping("/post/{postId}/favorite/status")
    public ApiResponse<FavoriteStatusResponse> favoriteStatus(@PathVariable("postId") Long postId) {
        return ApiResponse.ok(favoriteService.favoriteStatus(postId, currentUserId()));
    }

    @Operation(summary = "关注用户（幂等 PUT，兼容旧 POST）")
    @RequestMapping(value = "/user/{userId}/follow", method = {RequestMethod.PUT, RequestMethod.POST})
    public ApiResponse<Void> follow(@PathVariable("userId") Long userId) {
        followService.follow(userId, currentUserId());
        return ApiResponse.ok();
    }

    @DeleteMapping("/user/{userId}/follow")
    public ApiResponse<Void> unfollow(@PathVariable("userId") Long userId) {
        followService.unfollow(userId, currentUserId());
        return ApiResponse.ok();
    }

    @GetMapping("/user/{userId}/follow/status")
    public ApiResponse<FollowStatusResponse> followStatus(@PathVariable("userId") Long userId) {
        return ApiResponse.ok(followService.followStatus(userId, currentUserId()));
    }

    @GetMapping("/search")
    public ApiResponse<List<PostResponse>> search(
            @RequestParam("keyword") String keyword,
            @RequestParam("page") int page,
            @RequestParam("size") int size) {
        return ApiResponse.ok(searchService.search(keyword, page, size));
    }

    private Long currentUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
