package com.mware.community.biz.count;

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
public interface LikePendingCounter {

    String PENDING_KEY = "like:counter:pending";

    /** 累加待刷增量：LIKE +1 / UNLIKE -1（HINCRBY 原子） */
    void increment(Long postId, int delta);

    /**
     * 取走全部待刷增量并清空（下次窗口从 0 开始累计）。
     *
     * @return postId → 待刷 delta（空表示无待刷增量）
     */
    Map<Long, Long> drainAndReset();
}
