package com.mware.community.biz.count.impl;

import com.mware.community.biz.count.LikePendingCounter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 点赞待刷增量计数器实现（Redis Hash：like:counter:pending）。
 */
@Component
public class LikePendingCounterImpl implements LikePendingCounter {

    private final StringRedisTemplate redisTemplate;

    public LikePendingCounterImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void increment(Long postId, int delta) {
        redisTemplate.opsForHash().increment(PENDING_KEY, String.valueOf(postId), delta);
    }

    /**
     * 当前实现 HGETALL + DEL 两步非原子：若 flush 与 count 消费者并发，可能丢增量。
     * TODO[原子性]：改用 Lua 脚本一次完成「读全部 + DEL」；
     *   或先 HGETALL 再删除已取出的 hashKey（HMDEL），把未刷入的残留留给下个窗口。
     */
    @Override
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
