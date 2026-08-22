package com.mware.community.biz.post.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mware.common.web.ApiException;
import com.mware.common.web.ErrorCode;
import com.mware.common.web.UserContext;
import com.mware.community.biz.post.PostService;
import com.mware.community.biz.like.LikeRedisStore;
import com.mware.community.biz.favorite.FavoriteRedisStore;
import com.mware.community.domain.CommunityPost;
import com.mware.community.dto.request.CreatePostRequest;
import com.mware.community.dto.response.PostResponse;
import com.mware.community.mapper.CommunityPostMapper;
import com.mware.community.mapper.CommentMapper;
import com.mware.community.mapper.PostFavoriteMapper;
import com.mware.community.mapper.PostLikeMapper;
import com.mware.community.domain.Comment;
import com.mware.community.domain.PostFavorite;
import com.mware.community.domain.PostLike;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 帖子 CRUD、分页和详情缓存实现。
 */
@Service
public class PostServiceImpl implements PostService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int TITLE_MAX_LENGTH = 255;
    private static final String CACHE_PREFIX = "community:post:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final CommunityPostMapper communityPostMapper;
    private final CommentMapper commentMapper;
    private final PostLikeMapper postLikeMapper;
    private final PostFavoriteMapper postFavoriteMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final LikeRedisStore likeRedisStore;
    private final FavoriteRedisStore favoriteRedisStore;

    public PostServiceImpl(CommunityPostMapper communityPostMapper,
                           CommentMapper commentMapper,
                           PostLikeMapper postLikeMapper,
                           PostFavoriteMapper postFavoriteMapper,
                           RedisTemplate<String, Object> redisTemplate,
                           LikeRedisStore likeRedisStore,
                           FavoriteRedisStore favoriteRedisStore) {
        this.communityPostMapper = communityPostMapper;
        this.commentMapper = commentMapper;
        this.postLikeMapper = postLikeMapper;
        this.postFavoriteMapper = postFavoriteMapper;
        this.redisTemplate = redisTemplate;
        this.likeRedisStore = likeRedisStore;
        this.favoriteRedisStore = favoriteRedisStore;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PostResponse createPost(CreatePostRequest request) {
        validateRequest(request);
        LocalDateTime now = LocalDateTime.now();
        CommunityPost post = CommunityPost.builder()
                .title(request.getTitle().trim())
                .content(request.getContent().trim())
                .authorId(currentUserId())
                .likeCount(0L)
                .likeVersion(0L)
                .favoriteCount(0L)
                .favoriteVersion(0L)
                .commentCount(0L)
                .createdAt(now)
                .updatedAt(now)
                .build();
        communityPostMapper.insert(post);
        return toPostResponse(post);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PostResponse updatePost(Long postId, CreatePostRequest request) {
        validatePostId(postId);
        validateRequest(request);
        CommunityPost post = requirePost(postId);
        requireAuthor(post);
        post.setTitle(request.getTitle().trim());
        post.setContent(request.getContent().trim());
        post.setUpdatedAt(LocalDateTime.now());
        communityPostMapper.updateById(post);
        evictCacheAfterCommit(postId);
        return toPostResponse(post);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePost(Long postId) {
        validatePostId(postId);
        CommunityPost post = requirePost(postId);
        requireAuthor(post);

        commentMapper.delete(new LambdaQueryWrapper<Comment>().eq(Comment::getPostId, postId));
        postLikeMapper.delete(new LambdaQueryWrapper<PostLike>().eq(PostLike::getPostId, postId));
        postFavoriteMapper.delete(new LambdaQueryWrapper<PostFavorite>().eq(PostFavorite::getPostId, postId));
        communityPostMapper.deleteById(postId);
        cleanPostStateAfterCommit(postId);
    }

    @Override
    public PostResponse getPost(Long postId) {
        validatePostId(postId);
        Object cached = redisTemplate.opsForValue().get(cacheKey(postId));
        if (cached instanceof PostResponse response) {
            return response;
        }

        PostResponse response = toPostResponse(requirePost(postId));
        redisTemplate.opsForValue().set(cacheKey(postId), response, CACHE_TTL);
        return response;
    }

    @Override
    public List<PostResponse> pagePosts(int page, int size) {
        validatePage(page, size);
        Page<CommunityPost> postPage = new Page<>(page, size);
        communityPostMapper.selectPage(postPage, new LambdaQueryWrapper<CommunityPost>()
                .orderByDesc(CommunityPost::getCreatedAt));
        return postPage.getRecords().stream().map(this::toPostResponse).toList();
    }

    private CommunityPost requirePost(Long postId) {
        CommunityPost post = communityPostMapper.selectById(postId);
        if (post == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "帖子不存在");
        }
        return post;
    }

    private void requireAuthor(CommunityPost post) {
        if (!post.getAuthorId().equals(currentUserId())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "仅作者可操作自己的帖子");
        }
    }

    private void validateRequest(CreatePostRequest request) {
        if (request == null || request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            throw new ApiException(ErrorCode.PARAM_INVALID, "帖子标题不能为空");
        }
        if (request.getTitle().trim().length() > TITLE_MAX_LENGTH) {
            throw new ApiException(ErrorCode.PARAM_INVALID, "帖子标题不能超过 255 字");
        }
        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            throw new ApiException(ErrorCode.PARAM_INVALID, "帖子内容不能为空");
        }
    }

    private void validatePostId(Long postId) {
        if (postId == null || postId <= 0) {
            throw new ApiException(ErrorCode.PARAM_INVALID, "帖子 ID 必须为正数");
        }
    }

    private void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(ErrorCode.PARAM_INVALID, "分页参数非法");
        }
    }

    private String cacheKey(Long postId) {
        return CACHE_PREFIX + postId;
    }

    private void evictCacheAfterCommit(Long postId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                redisTemplate.delete(cacheKey(postId));
            }
        });
    }

    private void cleanPostStateAfterCommit(Long postId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                redisTemplate.delete(cacheKey(postId));
                likeRedisStore.deletePostState(postId);
                favoriteRedisStore.deletePostState(postId);
            }
        });
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

    /**
     * domain → dto 映射。likeCount / favoriteCount / commentCount 为异步聚合写入的最终一致值；
     * 未聚合完成（null）时兜底 0。
     */
    private PostResponse toPostResponse(CommunityPost post) {
        return PostResponse.builder()
                .id(post.getId())
                .title(post.getTitle())
                .content(post.getContent())
                .authorId(post.getAuthorId())
                .likeCount(post.getLikeCount() != null ? post.getLikeCount() : 0L)
                .favoriteCount(post.getFavoriteCount() != null ? post.getFavoriteCount() : 0L)
                .commentCount(post.getCommentCount() != null ? post.getCommentCount() : 0L)
                .createdAt(post.getCreatedAt())
                .build();
    }
}
