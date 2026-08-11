package com.mware.community.biz.count;

/**
 * 点赞计数刷库调度器：把 Redis 待刷增量（like:counter:pending）批量写入 community_post.like_count。
 * <p>
 * 批量聚合（非逐条）：
 * <pre>
 *   postId=1001 → +4   →  UPDATE community_post SET like_count = like_count + 4  WHERE id = 1001
 *   postId=1002 → +17
 *   postId=1003 → -2
 * </pre>
 * 与逐条 UPDATE 相比，把 N 条请求压缩成 M 条 UPDATE（M << N），大幅降低 DB 写放大。
 * <p>
 * 实验点：聚合窗口（flush-interval-ms）/ 批大小 / 幂等（delta 是相对增量，重复 flush 会重复累加 →
 * 依赖 count 消费者的 consumer_event 幂等从源头避免重复，flush 本身只消费 Redis 增量一次）。
 * 失败补偿：UPDATE 抛异常时当前实现会丢失该批增量（Redis 已 DEL）——
 * TODO[补偿]：逐条回滚到 pending（HINCRBY 加回 delta），或改为「update 失败不 DEL」的两阶段取数。
 */
public interface LikeCountFlushScheduler {

    /** 定时批量刷待刷计数到 community_post.like_count（@Scheduled 触发） */
    void flush();
}
