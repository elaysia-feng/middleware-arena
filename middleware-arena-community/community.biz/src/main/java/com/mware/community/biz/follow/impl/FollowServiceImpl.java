package com.mware.community.biz.follow.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mware.common.web.ApiException;
import com.mware.common.web.ErrorCode;
import com.mware.community.biz.follow.FollowService;
import com.mware.community.domain.Follow;
import com.mware.community.dto.response.FollowStatusResponse;
import com.mware.community.mapper.FollowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * 关注业务实现。关注关系写 MySQL，唯一键保证并发重复请求不会产生重复关系。
 */
@Service
public class FollowServiceImpl implements FollowService {

    private final FollowMapper followMapper;

    public FollowServiceImpl(FollowMapper followMapper) {
        this.followMapper = followMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void follow(Long authorId, Long userId) {
        validate(authorId, userId);
        // INSERT IGNORE + (author_id,user_id) 唯一键保证重复关注和并发关注均幂等。
        followMapper.insertIgnore(authorId, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unfollow(Long authorId, Long userId) {
        validate(authorId, userId);
        // 删除不存在的关系影响 0 行，DELETE 仍按幂等成功处理。
        followMapper.delete(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getAuthorId, authorId)
                .eq(Follow::getUserId, userId));
    }

    @Override
    public FollowStatusResponse followStatus(Long authorId, Long userId) {
        validate(authorId, userId);
        return FollowStatusResponse.builder()
                .userId(authorId)
                .following(exists(authorId, userId))
                .build();
    }

    private boolean exists(Long authorId, Long userId) {
        // 这里只查询是否存在，不加载完整 Follow 实体。
        return followMapper.selectCount(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getAuthorId, authorId)
                .eq(Follow::getUserId, userId)) > 0;
    }

    private void validate(Long authorId, Long userId) {
        if (authorId == null || authorId <= 0 || userId == null || userId <= 0) {
            throw new ApiException(ErrorCode.PARAM_INVALID, "用户 ID 必须为正数");
        }
        if (authorId.equals(userId)) {
            throw new ApiException(ErrorCode.PARAM_INVALID, "不能关注自己");
        }
    }
}
