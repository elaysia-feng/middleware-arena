package com.mware.community.biz;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mware.common.web.ApiException;
import com.mware.common.web.ErrorCode;
import com.mware.common.web.UserContext;
import com.mware.community.domain.Comment;
import com.mware.community.domain.CommunityPost;
import com.mware.community.domain.EventOutbox;
import com.mware.community.domain.PostFavorite;
import com.mware.community.domain.PostLike;
import com.mware.community.dto.message.LikeEvent;
import com.mware.community.dto.request.CommentRequest;
import com.mware.community.dto.request.CreatePostRequest;
import com.mware.community.dto.response.CommentResponse;
import com.mware.community.dto.response.LikeStatusResponse;
import com.mware.community.dto.response.PostResponse;
import com.mware.community.mapper.CommentMapper;
import com.mware.community.mapper.CommunityPostMapper;
import com.mware.community.mapper.EventOutboxMapper;
import com.mware.community.mapper.PostFavoriteMapper;
import com.mware.community.mapper.PostLikeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 社区业务实现（骨架占位）。
 * <p>
 * TODO[社区]：接入 community.mapper + Redis + ES 后，按各方法 1.2.3. 编号步骤逐个实现。
 * Request→domain、domain→Response 映射统一在本实现内部完成，Controller 只做薄转发。
 */
@Service
public class CommunityServiceImpl implements CommunityService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final CommunityPostMapper communityPostMapper;
    private final CommentMapper commentMapper;
    private final PostLikeMapper postLikeMapper;
    private final PostFavoriteMapper postFavoriteMapper;
    private final EventOutboxMapper eventOutboxMapper;

    public CommunityServiceImpl(CommunityPostMapper communityPostMapper,
                                CommentMapper commentMapper,
                                PostLikeMapper postLikeMapper,
                                PostFavoriteMapper postFavoriteMapper,
                                EventOutboxMapper eventOutboxMapper) {
        this.communityPostMapper = communityPostMapper;
        this.commentMapper = commentMapper;
        this.postLikeMapper = postLikeMapper;
        this.postFavoriteMapper = postFavoriteMapper;
        this.eventOutboxMapper = eventOutboxMapper;
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

    @Override
    public CommentResponse addComment(Long postId, CommentRequest request) {
        // Request → domain：postId 以路径为准、authorId 从 UserContext 注入，防伪造
        Comment comment = Comment.builder()
                .postId(postId)
                .authorId(currentUserId())
                .content(request.getContent())
                .build();
        // TODO[社区]：发表评论
        //   1. 校验 postId 对应帖子存在（可复用 getPost 校验逻辑）
        //   2. 若 parentId 非空：校验父评论存在且 parent.postId == comment.postId（防跨帖回复）
        //   3. 新增 CommentMapper（@TableName("community_comment") 对齐 sql/init.sql，含 id 主键），insert 后回填 id
        //   4. 返回带 id 的 comment
        return toCommentResponse(comment);
    }

    @Override
    public List<CommentResponse> pageComments(Long postId, int page, int size) {
        // TODO[社区]：帖子评论分页
        //   1. 构造 Page<Comment>(page, size)
        //   2. commentMapper.selectPage(page, wrapper where post_id = #{postId} orderBy asc created_at)
        //   3. 返回 page.getRecords() 并逐个映射为 CommentResponse
        return null;
    }

    /**
     * 点赞 / 取消点赞 —— <b>Transactional Outbox</b>：
     * <pre>
     *   BEGIN
     *     INSERT INTO post_like(...)      -- 点赞事实（最终事实唯一来源）
     *     INSERT INTO event_outbox(...)   -- 待发送事件（LIKE / UNLIKE）
     *   COMMIT
     *      ↓ OutboxRelay 扫描 PENDING → RabbitMQ(fanout) → count/cache/statistics 消费者
     * </pre>
     * 本地事务提交即"事件已入账"，DB 成功但 MQ 失败由 outbox 重扫补偿，不丢事件。
     * 写 MySQL 不直接碰 Redis：点赞状态先由 cache 消费者回写 Redis，计数由 count 消费者聚合（最终一致）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void like(Long postId, Long userId) {
        // 1. 判定当前状态：post_like（post_id + user_id 唯一）是否有行
        Long existCount = postLikeMapper.selectCount(new LambdaQueryWrapper<PostLike>()
                .eq(PostLike::getPostId, postId)
                .eq(PostLike::getUserId, userId));
        boolean liked = existCount != null && existCount > 0;
        String action = liked ? LikeEvent.ACTION_UNLIKE : LikeEvent.ACTION_LIKE;

        // 2. 写点赞事实：未点赞 → 插入；已点赞 → 删除（并发冲突抛唯一键异常 → 事务回滚，两端一致）
        if (liked) {
            postLikeMapper.delete(new LambdaQueryWrapper<PostLike>()
                    .eq(PostLike::getPostId, postId)
                    .eq(PostLike::getUserId, userId));
        } else {
            postLikeMapper.insert(PostLike.builder()
                    .postId(postId)
                    .userId(userId)
                    .createdAt(LocalDateTime.now())
                    .build());
        }

        // 3. 同事务写 outbox 事件（OutboxRelay 异步投递，勿在此直接发 MQ）
        eventOutboxMapper.insert(buildOutbox(postId, userId, action));
    }

    @Override
    public LikeStatusResponse likeStatus(Long postId) {
        // TODO[社区]：点赞状态（读路径最终一致）
        //   1. liked：Redis SISMEMBER like:users:{postId} userId（cache 消费者写入的 Set）
        //      或 Bitmap：GETBIT like:bitmap:{postId} userId
        //   2. likeCount：Redis GET like:count:{postId}，未命中降级读 community_post.like_count
        //   3. 点赞状态是公开信息，无需归属校验
        //   （当前返回占位值，接入 RedisTemplate 后实现）
        return LikeStatusResponse.builder()
                .postId(postId)
                .liked(false)
                .likeCount(0L)
                .build();
    }

    /** 组装 outbox 事件行：eventId=UUID、payload=LikeEvent JSON */
    private EventOutbox buildOutbox(Long postId, Long userId, String action) {
        LikeEvent event = LikeEvent.builder()
                .eventId(UUID.randomUUID().toString().replace("-", ""))
                .postId(postId)
                .userId(userId)
                .action(action)
                .timestamp(Instant.now().getEpochSecond())
                .build();
        try {
            return EventOutbox.builder()
                    .eventId(event.getEventId())
                    .aggregateId(postId)
                    .eventType(action)
                    .payload(OBJECT_MAPPER.writeValueAsString(event))
                    .status(EventOutbox.STATUS_PENDING)
                    .createdAt(LocalDateTime.now())
                    .build();
        } catch (JsonProcessingException e) {
            // 事件序列化失败属系统错误：事务回滚，点赞事实不落库
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }
    }

    @Override
    public void favorite(Long postId, Long userId) {
        // TODO[社区]：收藏 / 取消收藏（表 post_favorite，post_id + user_id 唯一）
        //   1. postFavoriteMapper.selectCount(eq post_id, eq user_id) 判定是否已收藏
        //   2. 不存在：insert（收藏）
        //   3. 存在：deleteById（取消收藏）
        //   4. 收藏数同点赞链路走 outbox + MQ 异步聚合（eventType=FAVORITE/UNFAVORITE）
    }

    @Override
    public void follow(Long authorId, Long userId) {
        // TODO[社区]：关注 / 取消关注
        //   1. 校验 authorId != userId（不能关注自己）
        //   2. 查询 community_follow 是否存在（author_id + user_id 唯一）
        //   3. 不存在：insert（关注，author_id = 被关注者，user_id = 关注者）
        //   4. 存在：deleteById（取消关注）
    }

    @Override
    public List<PostResponse> search(String keyword, int page, int size) {
        // TODO[社区]：ES 全文搜索
        //   1. ES 查询 community_post 索引：title + content 字段 matchQuery，关键词高亮
        //   2. 分页返回命中结果
        //   3. 兜底方案：ES 不可用时降级为 MySQL LIKE（title / content contains keyword）
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

    /**
     * domain → dto 映射。
     */
    private CommentResponse toCommentResponse(Comment comment) {
        return CommentResponse.builder()
                .id(comment.getId())
                .postId(comment.getPostId())
                .authorId(comment.getAuthorId())
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .build();
    }
}
