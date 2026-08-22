package com.mware.auth.controller;

import com.mware.auth.biz.AuthService;
import com.mware.auth.dto.request.LoginRequest;
import com.mware.auth.dto.request.RefreshRequest;
import com.mware.auth.dto.request.RegisterRequest;
import com.mware.auth.dto.response.LoginResponse;
import com.mware.auth.dto.response.MembershipResponse;
import com.mware.auth.dto.response.UserInfoResponse;
import com.mware.common.web.ApiException;
import com.mware.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：注册 / 登录 / 刷新 / 登出 / 当前用户。
 * <p>
 * 统一返回 {@link ApiResponse}，业务异常由 GlobalExceptionHandler 兜底。
 */
@Tag(name = "认证")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Value("${ma.internal-token:middleware-arena-internal-token}")
    private String internalToken;

    @Operation(summary = "注册：密码 BCrypt 加密后落库，成功后直接返回双 token")
    @PostMapping("/register")
    public ApiResponse<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @Operation(summary = "登录：返回 accessToken + refreshToken")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @Operation(summary = "刷新：用 refreshToken 换新双 token（旧 refreshToken 作废）")
    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok(authService.refresh(request));
    }

    @Operation(summary = "登出：删除 refreshToken，立即失效")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.getRefreshToken());
        return ApiResponse.ok(null);
    }

    @Operation(summary = "当前用户信息：从 Authorization: Bearer 解析 accessToken")
    @GetMapping("/me")
    public ApiResponse<UserInfoResponse> me(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.ok(authService.me(extractToken(authorization)));
    }

    @Operation(summary = "模拟充值 30 天 VIP")
    @PostMapping("/vip/mock-recharge")
    public ApiResponse<MembershipResponse> mockRecharge(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.ok(authService.mockRecharge(extractToken(authorization)));
    }

    @Operation(summary = "内部服务查询用户会员状态")
    @GetMapping("/internal/users/{userId}/membership")
    public ApiResponse<MembershipResponse> membership(
            @PathVariable("userId") Long userId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        if (!internalToken.equals(token)) {
            throw new ApiException(403, "内部服务凭证无效");
        }
        return ApiResponse.ok(authService.membership(userId));
    }

    /** 从 "Bearer xxxx" 中取出 token，缺头 / 缺 Bearer 前缀统一抛 401 */
    private String extractToken(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            throw new ApiException(401, "请携带 Authorization: Bearer {accessToken}");
        }
        return authorization.substring("Bearer ".length()).trim();
    }
}
