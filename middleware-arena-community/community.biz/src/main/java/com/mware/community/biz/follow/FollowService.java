package com.mware.community.biz.follow;

import com.mware.community.dto.response.FollowStatusResponse;

/**
 * 关注业务接口（面向接口编程）。
 * <p>
 * 对外只暴露 DTO，domain 实体仅存在于实现内部。实现见 {@code impl/FollowServiceImpl}。
 */
public interface FollowService {

    /** 关注（authorId=被关注者，userId=关注者，幂等）。 */
    void follow(Long authorId, Long userId);

    /** 取消关注（幂等）。 */
    void unfollow(Long authorId, Long userId);

    /** 查询关注状态。 */
    FollowStatusResponse followStatus(Long authorId, Long userId);
}
