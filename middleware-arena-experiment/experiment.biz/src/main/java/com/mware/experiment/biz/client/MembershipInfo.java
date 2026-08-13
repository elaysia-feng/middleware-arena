package com.mware.experiment.biz.client;

import lombok.Data;

/** auth-service 返回的会员查询结果，仅保留任务路由需要的字段。 */
@Data
public class MembershipInfo {
    private Long userId;
    private String tier;
    private String effectiveTier;
    private java.time.LocalDateTime vipExpireAt;
}
