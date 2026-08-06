package com.mware.common.jwt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置：ma.jwt.*
 * <p>
 * TODO[双 token 登录]：接入真正的登录时，access 短效(15~30min) + refresh 长效(7d)，
 * refreshToken 存 Redis 支持轮换与登出拉黑。
 */
@Data
@ConfigurationProperties(prefix = "ma.jwt")
public class JwtProperties {

    /** HS256 密钥，至少 32 字节（生产环境务必通过环境变量覆盖） */
    private String secret = "middleware-arena-default-secret-key-please-override-2026";

    /** access token 有效期（默认 15 分钟） */
    private long accessTtlMs = 15 * 60 * 1000L;

    /** refresh token 有效期（默认 7 天，双 token 接入后使用） */
    private long refreshTtlMs = 7 * 24 * 60 * 60 * 1000L;
}
