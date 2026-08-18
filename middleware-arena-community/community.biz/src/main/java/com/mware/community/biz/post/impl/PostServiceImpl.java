package com.mware.community.biz.post.impl;

import com.mware.common.web.ApiException;
import com.mware.common.web.ErrorCode;
import com.mware.common.web.UserContext;
import com.mware.community.biz.post.PostService;
import com.mware.community.domain.CommunityPost;
import com.mware.community.dto.request.CreatePostRequest;
import com.mware.community.dto.response.PostResponse;
import com.mware.community.mapper.CommunityPostMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 帖子业务实现（骨架占位）。
 * <p>
 * TODO[社区]：接入 community.mapper 后，按各方法 1.2.3. 编号步骤逐个实现。
 * Request→domain、domain→Response 映射统一在本实现内部完成，Controller 只做薄转发。
 */
@Service
public class PostServiceImpl implements PostService {

    private final CommunityPostMapper communityPostMapper;

    public PostServiceImpl(CommunityPostMapper communityPostMapper) {
        this.communityPostMapper = communityPostMapper;
    }

    @Override
    public PostResponse createPost(CreatePostRequest request) {
        // Request → domain：authorId 从 UserContext 注入，防伪造
        CommunityPost post = CommunityPost.builder()
                .title(request.getTitle())
                .content(request.getContent())
                .authorId(currentUserId())
                .build();
        // TODO[社区]：发布帖子
        //   1. 校验 title / content 非空，非法抛 ApiException(PARAM_INVALID)
        //   2. 补默认值：createdAt / updatedAt（authorId 已在 Service 从 UserContext 注入，防伪造）
        //   3. communityPostMapper.insert(post)，MyBatis-Plus 回填自增 id
        //   4. 返回带 id 的 post
        return toPostResponse(post);
    }

    @Override
    public PostResponse updatePost(Long postId, CreatePostRequest request) {
        // Request → domain：authorId 从 UserContext 注入，供作者权限校验；postId 以路径为准
        CommunityPost post = CommunityPost.builder()
                .id(postId)
                .title(request.getTitle())
                .content(request.getContent())
                .authorId(currentUserId())
                .build();
        // TODO[社区]：编辑帖子
        //   1. communityPostMapper.selectById(post.getId())，不存在抛 ApiException(NOT_FOUND)
        //   2. 校验作者权限：post.authorId == 当前登录用户，防越权改他人帖子
        //   3. communityPostMapper.updateById(post)，由 DB 刷新 updated_at
        //   4. 返回更新后的 post
        return toPostResponse(post);
    }

    @Override
    public void deletePost(Long postId) {
        // TODO[社区]：删除帖子
        //   1. 校验帖子存在 + 作者权限（同 updatePost 1/2）
        //   2. communityPostMapper.deleteById(postId)
        //   3. 级联清理：删除该帖的评论 / 点赞 / 收藏记录
    }

    @Override
    public PostResponse getPost(Long postId) {
        // TODO[社区]：帖子详情
        //   1. Redis Cache-Aside：先读缓存，命中直接返回
        //   2. 未命中则 communityPostMapper.selectById(postId)，不存在抛 ApiException(NOT_FOUND)
        //   3. 写回 Redis 缓存（设置过期时间）
        return null;
    }

    @Override
    public List<PostResponse> pagePosts(int page, int size) {
        // TODO[社区]：帖子分页列表
        //   1. 构造 Page<CommunityPost>(page, size)
        //   2. communityPostMapper.selectPage(page, wrapper orderByDesc created_at)
        //   3. 返回 page.getRecords() 并逐个映射为 PostResponse
        return null;
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
