package com.mware.community.biz.comment.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mware.common.web.ApiException;
import com.mware.common.web.ErrorCode;
import com.mware.common.web.UserContext;
import com.mware.community.biz.comment.CommentCache;
import com.mware.community.biz.comment.CommentService;
import com.mware.community.domain.Comment;
import com.mware.community.domain.CommunityPost;
import com.mware.community.dto.request.CommentRequest;
import com.mware.community.dto.response.CommentResponse;
import com.mware.community.mapper.CommentMapper;
import com.mware.community.mapper.CommunityPostMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评论业务实现。
 * <p>
 * 一级评论 + 回复共用本服务：parentId 为空 = 一级评论，非空 = 回复。
 * 回复链路在 Service 层强制校验（必须同帖，禁止跨帖回复，禁止对回复再回复）。
 * <p>
 * Request→domain、domain→Response 映射统一在本实现内部完成，Controller 只做薄转发。
 * <p>
 * 计数列（{@code CommunityPost.commentCount}）不在本服务内同步 +1/-1，
 * 由 outbox 异步聚合链路写入（最终一致，见 PostServiceImpl 注释）。
 * <p>
 * <b>缓存（v1）</b>：仅帖子评论分页前 3 页走 Redis Cache-Aside，TTL 5min；
 * 评论增删不主动失效缓存，靠 TTL 自然过期（5min 脏读可接受）。
 * 详见 {@link CommentCache} 的设计决策注释。
 */
@Service
public class CommentServiceImpl implements CommentService {

    private static final Logger log = LoggerFactory.getLogger(CommentServiceImpl.class);

    /** 评论内容最大长度（字符数），超长直接拒，避免单评论过大拖垮列表渲染 */
    private static final int CONTENT_MAX_LEN = 500;

    /** 分页最大 size，防深分页打爆 DB */
    private static final int MAX_PAGE_SIZE = 50;

    private final CommentMapper commentMapper;
    private final CommunityPostMapper communityPostMapper;
    private final CommentCache commentCache;
    private final RedisTemplate<String, Object> redisTemplate;

    public CommentServiceImpl(CommentMapper commentMapper,
                              CommunityPostMapper communityPostMapper,
                              CommentCache commentCache,
                              RedisTemplate<String, Object> redisTemplate) {
        this.commentMapper = commentMapper;
        this.communityPostMapper = communityPostMapper;
        this.commentCache = commentCache;
        this.redisTemplate = redisTemplate;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentResponse addComment(Long postId, CommentRequest request) {
        // 1. 内容校验：非空 + 长度上限
        //    业务侧先把关；Controller 层若再加 @Size 也行，本处不依赖
        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            throw new ApiException(ErrorCode.PARAM_INVALID, "评论内容不能为空");
        }
        if (request.getContent().length() > CONTENT_MAX_LEN) {
            throw new ApiException(ErrorCode.PARAM_INVALID, "评论内容超过 " + CONTENT_MAX_LEN + " 字");
        }

        // 2. 帖子存在性校验：避免给已删除/不存在帖子写评论
        CommunityPost post = communityPostMapper.selectById(postId);
        if (post == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "帖子不存在");
        }

        // 3. 回复链路校验：仅当 parentId 非空时执行
        //    - 父评论必须存在
        //    - 父评论必须挂在同一帖子（防跨帖回复 = 防伪造 / 防骚扰）
        //    - 一级评论才能被回复，禁止对回复再回复（避免无界嵌套，UI 层楼中楼也好折叠）
        if (request.getParentId() != null) {
            Comment parent = commentMapper.selectById(request.getParentId());
            if (parent == null) {
                throw new ApiException(ErrorCode.NOT_FOUND, "父评论不存在");
            }
            if (!parent.getPostId().equals(postId)) {
                // 跨帖回复：父评论属于别的帖子，直接拒绝（不暴露帖子归属详情，防探测）
                throw new ApiException(ErrorCode.FORBIDDEN, "禁止跨帖回复");
            }
            if (parent.getParentId() != null) {
                // 仅一级评论允许被回复，禁止对回复再回复
                throw new ApiException(ErrorCode.PARAM_INVALID, "暂不支持对回复再回复");
            }
        }

        // 4. 组装并入库：postId/authorId 来自路径与 UserContext（防伪造）
        //    时间字段在 Java 端塞，与 PostServiceImpl 口径保持一致；
        //    DB 列也有 DEFAULT CURRENT_TIMESTAMP 双保险，幂等
        Comment comment = Comment.builder()
                .postId(postId)
                .authorId(currentUserId())
                .parentId(request.getParentId())
                .content(request.getContent())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        commentMapper.insert(comment); // MyBatis-Plus 回填 comment.getId()

        // 5. 评论和计数在同一个本地事务提交，避免 outbox 关闭时计数长期不更新。
        communityPostMapper.changeCommentCount(postId, 1L);

        // 6. 事务提交后再访问 Redis，避免外部调用扩大数据库事务边界。
        evictCachesAfterCommit(postId);

        return toCommentResponse(comment);
    }

    @Override
    public List<CommentResponse> pageComments(Long postId, int page, int size) {
        // 1. 边界校验：page 从 1 开始；size 1~MAX_PAGE_SIZE（防深分页打爆 DB）
        if (page < 1 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(ErrorCode.PARAM_INVALID, "分页参数非法");
        }

        // 2. 仅前 3 页走缓存（Cache-Aside：命中返回缓存，未命中查 DB + 写缓存）
        if (commentCache.isCacheable(page)) {
            // 2.1 先探测缓存 key 是否存在
            //    hasKey 区分"未命中"vs"真没数据"；get() 返回空列表无法区分
            if (commentCache.hasKey(postId, page, size)) {
                // 命中：直接返回缓存值（5min 内的高频读全走 Redis，省 DB）
                return readCacheSafely(postId, page, size);
            }

            // 2.2 未命中：查 DB → 写缓存 → 返回
            List<CommentResponse> dbResult = queryCommentsFromDb(postId, page, size);
            writeCacheSafely(postId, page, size, dbResult);
            return dbResult;
        }

        // 3. 第 4+ 页直接走 DB（不缓存，避免深分页打爆 Redis 内存）
        return queryCommentsFromDb(postId, page, size);
    }

    @Override
    public List<CommentResponse> pageReplies(Long parentId, int page, int size) {
        // 1. 父评论存在性校验：查询不存在的 parentId 直接返回空列表前端不好排查，给个明确 404
        Comment parent = commentMapper.selectById(parentId);
        if (parent == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "父评论不存在");
        }

        // 2. 分页边界校验（同 pageComments）
        if (page < 1 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(ErrorCode.PARAM_INVALID, "分页参数非法");
        }

        // 3. 构造 MyBatis-Plus 分页 + 条件
        //    - 按 parentId 过滤（取该评论下的所有回复；保持接口通用，parent 可为一级或任意 comment）
        //    - 按创建时间正序（回复通常按时间正序展示，便于阅读上下文）
        // 注：v1 暂不缓存 pageReplies（与 pageComments 形态不同，缓存 key 设计需另算；后续按需扩展）
        Page<Comment> mpPage = new Page<>(page, size);
        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<Comment>()
                .eq(Comment::getParentId, parentId)
                .orderByAsc(Comment::getCreatedAt);

        // 4. 执行分页查询
        commentMapper.selectPage(mpPage, wrapper);

        // 5. domain → DTO 映射返回
        return mpPage.getRecords().stream().map(this::toCommentResponse).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Long postId, Long commentId) {
        // 1. 评论存在性校验
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "评论不存在");
        }

        // 2. 帖子一致性校验（防跨帖删除）：路径里 postId + commentId 必须匹配
        if (!comment.getPostId().equals(postId)) {
            // 评论属于别的帖子，禁止跨帖操作（不暴露帖子归属详情，防探测）
            throw new ApiException(ErrorCode.FORBIDDEN, "禁止跨帖操作");
        }

        // 3. 作者权限校验：仅作者可删除自己的评论
        //    管理员删除另开 adminDeleteComment，留 TODO
        if (!comment.getAuthorId().equals(currentUserId())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "仅作者可删除自己的评论");
        }
        // 当前公开接口只允许作者删除；管理员审核删除不混入本方法。

        // 4. 级联硬删：先删子回复，再删自己（顺序：先子后父，避免子记录短暂悬挂）
        //    用 delete(Wrapper) 而不是逐条 deleteById，单 SQL 搞定
        //    硬删 + 级联是当前骨架阶段选择；生产化应改为软删 + 审计日志（is_deleted / 状态字段 + 操作流水表）
        // 当前数据模型采用硬删；若引入审核/恢复能力，再单独增加软删字段和审计流水。
        long replyCount = commentMapper.selectCount(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getParentId, commentId));
        commentMapper.delete(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getParentId, commentId));
        commentMapper.deleteById(commentId);

        // 5. 删除一级评论时把其直接回复一并从帖子评论数扣除。
        communityPostMapper.changeCommentCount(postId, -(replyCount + 1L));

        // 6. 事务提交后再清理缓存。
        evictCachesAfterCommit(postId);
    }

    /**
     * 帖子评论分页：DB 查询逻辑（cacheable / 非 cacheable 共用）。
     * <p>
     * - 按 postId 过滤
     * - 按 createdAt 正序（一级 + 回复扁平，UI 端按 parentId 折叠）
     */
    private List<CommentResponse> queryCommentsFromDb(Long postId, int page, int size) {
        Page<Comment> mpPage = new Page<>(page, size);
        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<Comment>()
                .eq(Comment::getPostId, postId)
                .orderByAsc(Comment::getCreatedAt);
        commentMapper.selectPage(mpPage, wrapper);
        return mpPage.getRecords().stream().map(this::toCommentResponse).toList();
    }

    private void evictCachesAfterCommit(Long postId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                redisTemplate.delete("community:post:" + postId);
                commentCache.evictPost(postId);
            }
        });
    }

    /**
     * 安全读缓存：Redis 异常降级到"未命中"哨兵，不让缓存故障拖垮主链路。
     */
    private List<CommentResponse> readCacheSafely(Long postId, int page, int size) {
        try {
            return commentCache.get(postId, page, size);
        } catch (Exception e) {
            // Fail-open：Redis 故障不影响主链路
            log.warn("[CommentCache] Redis get failed, fallback to DB. postId={}, page={}, size={}",
                    postId, page, size, e);
            return java.util.Collections.emptyList();
        }
    }

    /**
     * 安全写缓存：Redis 异常吞掉，主流程已返回结果，缓存写失败不影响用户。
     */
    private void writeCacheSafely(Long postId, int page, int size, List<CommentResponse> value) {
        try {
            commentCache.put(postId, page, size, value);
        } catch (Exception e) {
            log.warn("[CommentCache] Redis put failed, ignore. postId={}, page={}, size={}",
                    postId, page, size, e);
        }
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
     * domain → dto 映射。
     */
    private CommentResponse toCommentResponse(Comment comment) {
        return CommentResponse.builder()
                .id(comment.getId())
                .postId(comment.getPostId())
                .authorId(comment.getAuthorId())
                .parentId(comment.getParentId())
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .build();
    }
}
