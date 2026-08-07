package com.mware.account.controller;

import com.mware.account.biz.AccountService;
import com.mware.account.domain.AccountBalance;
import com.mware.account.dto.response.AccountBalanceResponse;
import com.mware.common.web.ApiException;
import com.mware.common.web.ApiResponse;
import com.mware.common.web.ErrorCode;
import com.mware.common.web.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 账户接口（框架占位，返回统一 {@link ApiResponse}）。
 * <p>
 * TODO[Seata AT 参与方]：
 *   1. POST /account/deduct 扣减余额（Seata AT 参与方，由 order-service 通过 Feign 调用）
 *   2. undo_log 表需随 Seata AT 启用而创建（Seata AT 自动回滚用）
 */
@Tag(name = "账户")
@RestController
@RequestMapping("/account")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @Operation(summary = "健康检查")
    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        return ApiResponse.ok("pong");
    }

    /**
     * 扣减余额（框架占位）。
     * <p>
     * 注意：本端点是 <b>内部服务间端点</b>，由 order-service 通过 Feign 调用（扣的是订单归属用户的余额，
     * 因此 userId 是业务参数、非调用者身份，不能改成 UserContext）。
     * 防绕过网关直连伪造：依赖 {@code ma.internal-auth.secret} 配置后，
     * base.web 的 AuthHeaderInterceptor 会校验 X-User-Id / X-Sign（HMAC 签名）并填充 UserContext，
     * 未带合法签名的直连请求会被拦截并返回 401。启用分布式事务时请配合 Seata AT。
     */
    @Operation(summary = "扣减余额（内部服务间调用）")
    @PostMapping("/deduct")
    public ApiResponse<Void> deductBalance(@RequestParam Long userId,
                                           @RequestParam BigDecimal amount) {
        accountService.deductBalance(userId, amount);
        return ApiResponse.ok();
    }

    @Operation(summary = "查询余额")
    @GetMapping("/balance/{userId}")
    public ApiResponse<AccountBalanceResponse> getBalance(@PathVariable Long userId) {
        // 面向用户：只允许查自己的余额，防 IDOR
        Long currentUserId = UserContext.getUserId();
        if (currentUserId == null || !currentUserId.equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        AccountBalance balance = accountService.getBalance(userId);
        AccountBalanceResponse response = AccountBalanceResponse.builder()
                .userId(balance.getUserId())
                .balance(balance.getBalance())
                .build();
        return ApiResponse.ok(response);
    }
}
