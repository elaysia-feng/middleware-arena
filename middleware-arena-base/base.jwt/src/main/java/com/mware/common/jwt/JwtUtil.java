package com.mware.common.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具：签发 / 解析 access token。
 * <p>
 * 普通类（非 Spring Bean），由 {@link JwtAutoConfiguration} 以 @Bean 装配，
 * 服务依赖 base.jwt 即自动生效，无需任何扫描配置。
 * <p>
 * TODO[双 token 登录]：当前仅提供单 access token 能力；
 * 后续补充 createRefreshToken / 刷新轮换 / Redis 黑名单逻辑。
 */
public class JwtUtil {

    private final JwtProperties props;
    private final SecretKey key;

    public JwtUtil(JwtProperties props) {
        this.props = props;
        this.key = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(Long userId, String username) {
        return Jwts.builder()
                // 不可变的一般放在subject里面
                .subject(String.valueOf(userId))
                // username 只用于展示，放自定义 claim
                .claim("username", username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + props.getAccessTtlMs()))
                .signWith(key)
                .compact();
    }

    /** 解析并校验 token，非法/过期会抛异常 */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
