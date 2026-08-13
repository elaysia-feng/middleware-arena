package com.mware.auth.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前用户信息（/auth/me 返回）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserInfoResponse {

    private Long id;

    private String username;

    private String nickname;

    /** 当前实时生效的会员等级。 */
    private String tier;

    private java.time.LocalDateTime vipExpireAt;
}
