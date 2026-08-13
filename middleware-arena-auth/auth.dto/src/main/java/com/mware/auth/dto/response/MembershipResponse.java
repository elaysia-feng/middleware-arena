package com.mware.auth.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 用户当前会员状态。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MembershipResponse {

    private Long userId;

    /** 数据库存储的等级。 */
    private String tier;

    /** 结合到期时间实时计算出的有效等级。 */
    private String effectiveTier;

    private LocalDateTime vipExpireAt;
}
