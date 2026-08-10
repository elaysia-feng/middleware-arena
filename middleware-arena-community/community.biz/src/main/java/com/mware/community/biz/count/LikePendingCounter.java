package com.mware.community.biz.count;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 点赞待刷增量计数器（Redis Hash：like:counter:pending）。
 * <p>
 * 1 秒内大量 LIKE/UNLIKE 时，count 消费者不做逐条 UPDATE（写放大），而是累加到本计数器：
 * <pre>
 *   HINCRBY like:counter:pending 1001 1     -- postId=1001 点赞 +1
 *   HINCRBY like:counter:pending 1001 1
 *   HINCRBY like:counter:pending 1002 1
 *   HINCRBY like:counter:pending 1001 -1    -- 取消点赞
 *   → postId=1001 累计 +1，postId=1002 累计 +1
 * </pre>
 * 由 {@link LikeCountFlushScheduler} 定时 drain 后批量 UPDATE：
 * {@code UPDATE community_post SET like_count = like_count + {delta} WHERE id = {postId}}。
 * <p>
 * 相关实验点：聚合窗口（flush 间隔）/ 批大小 / MQ 堆积 / 刷盘失败补偿 / 消费者宕机恢复。
 */
@Component
public class LikePendingCounter {

    public static final String PENDING_KEY = "like:counter:pending";

    private final StringRedisTemplate redisTemplate;

    public LikePendingCounter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 累加待刷增量：LIKE +1 / UNLIKE -1（HINCRBY 原子） */
    public void increment(Long postId, int delta) {
        redisTemplate.opsForHash().increment(PENDING_KEY, String.valueOf(postId), delta);
    }

    /**
     * 取走全部待刷增量并清空（下次窗口从 0 开始累计）。
     * <p>
     * 当前实现 HGETALL + DEL 两步非原子：若 flush 与 count 消费者并发，可能丢增量。
     * TODO[原子性]：改用 Lua 脚本一次完成「读全部 + DEL」；
     *   或先 HGETALL 再删除已取出的 hashKey（HMDEL），把未刷入的残留留给下个窗口。
     *
     * @return postId → 待刷 delta（空表示无待刷增量）
     */
    public Map<Long, Long> drainAndReset() {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(PENDING_KEY);
        if (entries.isEmpty()) {
            return Map.of();
        }
        redisTemplate.delete(PENDING_KEY);
        Map<Long, Long> result = new HashMap<>(entries.size());
        entries.forEach((postId, delta) ->
                result.put(Long.valueOf(String.valueOf(postId)), Long.valueOf(String.valueOf(delta))));
        return result;
    }
}
