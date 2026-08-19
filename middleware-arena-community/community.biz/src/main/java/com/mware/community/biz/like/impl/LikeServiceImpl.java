package com.mware.community.biz.like.impl;

import com.mware.community.biz.like.LikeRedisStore;
import com.mware.community.biz.like.LikeService;
import com.mware.community.dto.response.LikeStatusResponse;
import org.springframework.stereotype.Service;

/**
 * 点赞业务实现。
 * <p>
 * 请求主链路只访问 Redis，不同步访问 MySQL：
 * Redis Lua 原子修改点赞状态 / 点赞数 / version，并写入 Redis Stream Outbox；
 * 后续由 LikeStreamRelay 转发 RabbitMQ，再异步持久化 MySQL 分片表。
 */
@Service
public class LikeServiceImpl implements LikeService {

    private final LikeRedisStore likeRedisStore;

    public LikeServiceImpl(LikeRedisStore likeRedisStore) {
        this.likeRedisStore = likeRedisStore;
    }

    @Override
    public void like(Long postId, Long userId) {
        // 点赞采用“设置目标状态”而不是 toggle，避免重复请求把状态反转。
        //   1. Lua 判断当前用户是否已经点赞，已点赞则幂等返回
        //   2. 未点赞则写用户点赞状态，并将帖子点赞数 +1、version +1
        //   3. 同一个 Lua 中 XADD Redis Stream，保证状态变化后一定存在待投递事件
        likeRedisStore.setLiked(postId, userId, true);
    }

    @Override
    public void unlike(Long postId, Long userId) {
        // 取消点赞同样采用目标状态语义。
        //   1. Lua 判断当前用户是否已经取消，已取消则幂等返回
        //   2. 已点赞则删除用户点赞状态，并将帖子点赞数 -1、version +1
        //   3. 同一个 Lua 中写 UNLIKE 事件到 Redis Stream，异步同步 MySQL
        likeRedisStore.setLiked(postId, userId, false);
    }

    @Override
    public LikeStatusResponse likeStatus(Long postId, Long userId) {
        // 点赞状态和实时点赞数均直接读取 Redis 写模型。
        // MySQL 是最终持久化事实层，因此这里不为了查询状态再回源 MySQL。
        return LikeStatusResponse.builder()
                .postId(postId)
                .liked(likeRedisStore.isLiked(postId, userId))
                .likeCount(likeRedisStore.likeCount(postId))
                .build();
    }
}
