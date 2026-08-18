package com.mware.community.biz.like.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mware.common.web.ApiException;
import com.mware.common.web.ErrorCode;
import com.mware.community.biz.like.LikeService;
import com.mware.community.domain.EventOutbox;
import com.mware.community.domain.PostLike;
import com.mware.community.dto.message.LikeEvent;
import com.mware.community.dto.response.LikeStatusResponse;
import com.mware.community.mapper.EventOutboxMapper;
import com.mware.community.mapper.PostLikeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 点赞业务实现。
 * <p>
 * {@code like} 已实现（事务性 Outbox）；{@code likeStatus} 待接入 RedisTemplate 后补齐。
 */
@Service
public class LikeServiceImpl implements LikeService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final PostLikeMapper postLikeMapper;
    private final EventOutboxMapper eventOutboxMapper;

    public LikeServiceImpl(PostLikeMapper postLikeMapper, EventOutboxMapper eventOutboxMapper) {
        this.postLikeMapper = postLikeMapper;
        this.eventOutboxMapper = eventOutboxMapper;
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
}
