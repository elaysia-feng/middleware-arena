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
import com.mware.community.dto.response.LikeStatusResponse;
import com.mware.community.dto.response.PostResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
        this.postService = postService; this.commentService = commentService; this.likeService = likeService;
        this.favoriteService = favoriteService; this.followService = followService; this.searchService = searchService;
    }

    @GetMapping("/ping") public ApiResponse<String> ping() { return ApiResponse.ok("pong"); }
    @PostMapping("/post/create") public ApiResponse<PostResponse> createPost(@RequestBody CreatePostRequest r) { return ApiResponse.ok(postService.createPost(r)); }
    @PutMapping("/post/{postId}") public ApiResponse<PostResponse> updatePost(@PathVariable Long postId, @RequestBody CreatePostRequest r) { return ApiResponse.ok(postService.updatePost(postId, r)); }
    @DeleteMapping("/post/{postId}") public ApiResponse<Void> deletePost(@PathVariable Long postId) { postService.deletePost(postId); return ApiResponse.ok(); }
    @GetMapping("/post/{postId}") public ApiResponse<PostResponse> getPost(@PathVariable Long postId) { return ApiResponse.ok(postService.getPost(postId)); }
    @GetMapping("/post/page") public ApiResponse<List<PostResponse>> pagePosts(@RequestParam int page, @RequestParam int size) { return ApiResponse.ok(postService.pagePosts(page, size)); }
    @PostMapping("/post/{postId}/comment") public ApiResponse<CommentResponse> addComment(@PathVariable Long postId, @RequestBody CommentRequest r) { return ApiResponse.ok(commentService.addComment(postId, r)); }
    @GetMapping("/post/{postId}/comment/page") public ApiResponse<List<CommentResponse>> pageComments(@PathVariable Long postId, @RequestParam int page, @RequestParam int size) { return ApiResponse.ok(commentService.pageComments(postId, page, size)); }
    @GetMapping("/comment/{parentId}/reply/page") public ApiResponse<List<CommentResponse>> pageReplies(@PathVariable Long parentId, @RequestParam int page, @RequestParam int size) { return ApiResponse.ok(commentService.pageReplies(parentId, page, size)); }
    @DeleteMapping("/post/{postId}/comment/{commentId}") public ApiResponse<Void> deleteComment(@PathVariable Long postId, @PathVariable Long commentId) { commentService.deleteComment(postId, commentId); return ApiResponse.ok(); }

    @Operation(summary = "点赞（幂等 PUT）")
    @PutMapping("/post/{postId}/like")
    public ApiResponse<Void> like(@PathVariable Long postId) { likeService.like(postId, currentUserId()); return ApiResponse.ok(); }

    @Operation(summary = "取消点赞（幂等 DELETE）")
    @DeleteMapping("/post/{postId}/like")
    public ApiResponse<Void> unlike(@PathVariable Long postId) { likeService.unlike(postId, currentUserId()); return ApiResponse.ok(); }

    @GetMapping("/post/{postId}/like/status")
    public ApiResponse<LikeStatusResponse> likeStatus(@PathVariable Long postId) { return ApiResponse.ok(likeService.likeStatus(postId, currentUserId())); }

    @PostMapping("/post/{postId}/favorite") public ApiResponse<Void> favorite(@PathVariable Long postId) { favoriteService.favorite(postId, currentUserId()); return ApiResponse.ok(); }
    @PostMapping("/user/{userId}/follow") public ApiResponse<Void> follow(@PathVariable Long userId) { followService.follow(userId, currentUserId()); return ApiResponse.ok(); }
    @GetMapping("/search") public ApiResponse<List<PostResponse>> search(@RequestParam String keyword, @RequestParam int page, @RequestParam int size) { return ApiResponse.ok(searchService.search(keyword, page, size)); }

    private Long currentUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) throw new ApiException(ErrorCode.UNAUTHORIZED);
        return userId;
    }
}
