package com.mware.community.biz.follow.impl;

import com.mware.community.biz.follow.FollowService;
import org.springframework.stereotype.Service;

/**
 * 关注业务实现（骨架占位）。
 * <p>
 * TODO[社区]：community_follow 表已在 sql/init.sql 建好，但缺 {@code Follow} 实体 + {@code FollowMapper}，
 * 需先补 domain/mapper 再接 biz。
 */
@Service
public class FollowServiceImpl implements FollowService {

    @Override
    public void follow(Long authorId, Long userId) {
        // TODO[社区]：关注 / 取消关注
        //   1. 校验 authorId != userId（不能关注自己）
        //   2. 查询 community_follow 是否存在（author_id + user_id 唯一）—— 需先补 Follow 实体 + FollowMapper
        //   3. 不存在：insert（关注，author_id = 被关注者，user_id = 关注者）
        //   4. 存在：deleteById（取消关注）
    }
}
