package com.mware.experiment.biz.client;

import com.mware.common.web.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/** 查询 auth-service 的实时会员状态。 */
@FeignClient(name = "auth-service")
public interface AuthMembershipClient {

    @GetMapping("/auth/internal/users/{userId}/membership")
    ApiResponse<MembershipInfo> membership(
            @PathVariable("userId") Long userId,
            @RequestHeader("X-Internal-Token") String internalToken);
}
