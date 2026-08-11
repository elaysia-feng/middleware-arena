package com.mware.community.biz.count.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mware.community.biz.count.LikeCountFlushScheduler;
import com.mware.community.biz.count.LikePendingCounter;
import com.mware.community.domain.CommunityPost;
import com.mware.community.mapper.CommunityPostMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 点赞计数刷库调度实现：把 Redis 待刷增量批量写入 community_post.like_count。
 */
@Component
@Slf4j
public class LikeCountFlushSchedulerImpl implements LikeCountFlushScheduler {

    private final LikePendingCounter pendingCounter;
    private final CommunityPostMapper communityPostMapper;

    public LikeCountFlushSchedulerImpl(LikePendingCounter pendingCounter, CommunityPostMapper communityPostMapper) {
        this.pendingCounter = pendingCounter;
        this.communityPostMapper = communityPostMapper;
    }

    @Override
    @Scheduled(fixedDelayString = "${community.outbox.flush-interval-ms:10000}")
    public void flush() {
        Map<Long, Long> deltas = pendingCounter.drainAndReset();
        if (deltas.isEmpty()) {
            return;
        }
        log.info("flush {} like-count deltas to community_post", deltas.size());
        deltas.forEach((postId, delta) -> communityPostMapper.update(null, new LambdaUpdateWrapper<CommunityPost>()
                .eq(CommunityPost::getId, postId)
                .setSql("like_count = like_count + " + delta)));
    }
}
