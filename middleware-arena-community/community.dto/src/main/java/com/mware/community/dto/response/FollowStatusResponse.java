package com.mware.community.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 当前登录用户对目标用户的关注状态响应。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FollowStatusResponse {
    /** 被查询的目标用户 ID。 */
    private Long userId;
    private Boolean following;
}
