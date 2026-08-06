package com.mware.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应（骨架占位）。
 * <p>
 * TODO[双 token 登录]：
 *   - accessToken：短效（15~30min），无状态 JWT，供每次请求携带
 *   - refreshToken：长效（7d），存 Redis，用于刷新 accessToken（轮换 + 登出拉黑）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private String accessToken;

    private String refreshToken;
}
