package com.mware.community.biz.follow;

/**
 * 关注业务接口（面向接口编程）。
 * <p>
 * 对外只暴露 DTO，domain 实体仅存在于实现内部。实现见 {@code impl/FollowServiceImpl}。
 */
public interface FollowService {

    /** 关注 / 取消关注（authorId=被关注者，userId=关注者） */
    void follow(Long authorId, Long userId);
}
