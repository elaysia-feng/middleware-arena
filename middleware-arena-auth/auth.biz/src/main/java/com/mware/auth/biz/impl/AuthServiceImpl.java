package com.mware.auth.biz.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mware.auth.biz.AuthService;
import com.mware.auth.domain.User;
import com.mware.auth.dto.request.LoginRequest;
import com.mware.auth.dto.request.RefreshRequest;
import com.mware.auth.dto.request.RegisterRequest;
import com.mware.auth.dto.response.LoginResponse;
import com.mware.auth.dto.response.MembershipResponse;
import com.mware.auth.dto.response.UserInfoResponse;
import com.mware.auth.mapper.UserMapper;
import com.mware.common.jwt.JwtProperties;
import com.mware.common.jwt.JwtUtil;
import com.mware.common.web.ApiException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 认证业务实现。
 * <p>
 * 密码一律 BCrypt 加密后落库，绝不存明文；
 * refreshToken 采用不透明随机串（非 JWT），存 Redis 天然支持轮换与登出拉黑。
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** Redis 中 refreshToken 的键前缀：ma:auth:refresh:{token} → userId */
    private static final String REFRESH_KEY_PREFIX = "ma:auth:refresh:";

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final JwtProperties jwtProps;
    private final StringRedisTemplate redisTemplate;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    @Transactional
    public LoginResponse register(RegisterRequest request) {
        Long count = userMapper.selectCount(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, request.getUsername()));
        if (count != null && count > 0) {
            throw new ApiException(400, "用户名已存在");
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname() == null || request.getNickname().isBlank()
                        ? request.getUsername() : request.getNickname())
                .tier("FREE")
                .build();
        userMapper.insert(user);

        return issueTokens(user);
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        User user = userMapper.selectOne(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, request.getUsername()));
        // 统一提示"用户名或密码错误"，避免暴露用户是否存在
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ApiException(400, "用户名或密码错误");
        }
        return issueTokens(user);
    }

    @Override
    public LoginResponse refresh(RefreshRequest request) {
        String refreshToken = request.getRefreshToken();
        String key = REFRESH_KEY_PREFIX + refreshToken;
        String uid = redisTemplate.opsForValue().get(key);
        if (uid == null) {
            throw new ApiException(401, "refreshToken 无效或已过期");
        }
        // 刷新轮换：旧 refreshToken 立即作废，只留新 token
        redisTemplate.delete(key);

        User user = userMapper.selectById(Long.valueOf(uid));
        if (user == null) {
            throw new ApiException(401, "用户不存在");
        }
        return issueTokens(user);
    }

    @Override
    public void logout(String refreshToken) {
        redisTemplate.delete(REFRESH_KEY_PREFIX + refreshToken);
    }

    @Override
    public UserInfoResponse me(String accessToken) {
        User user = userMapper.selectById(parseUserId(accessToken));
        if (user == null) {
            throw new ApiException(401, "用户不存在");
        }
        return UserInfoResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .tier(effectiveTier(user, LocalDateTime.now()))
                .vipExpireAt(user.getVipExpireAt())
                .build();
    }

    @Override
    @Transactional
    public MembershipResponse mockRecharge(String accessToken) {
        User user = userMapper.selectById(parseUserId(accessToken));
        if (user == null) {
            throw new ApiException(401, "用户不存在");
        }

        // 1. 未到期从原到期时间续费；已到期从当前时间重新计算。
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startAt = user.getVipExpireAt() != null && user.getVipExpireAt().isAfter(now)
                ? user.getVipExpireAt() : now;
        user.setTier("VIP");
        user.setVipExpireAt(startAt.plusDays(30));
        userMapper.updateById(user);
        return toMembershipResponse(user, now);
    }

    @Override
    public MembershipResponse membership(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new ApiException(404, "用户不存在");
        }
        return toMembershipResponse(user, LocalDateTime.now());
    }

    private MembershipResponse toMembershipResponse(User user, LocalDateTime now) {
        return MembershipResponse.builder()
                .userId(user.getId())
                .tier(user.getTier())
                .effectiveTier(effectiveTier(user, now))
                .vipExpireAt(user.getVipExpireAt())
                .build();
    }

    /** 不修改数据库，所有 VIP 功能入口都根据到期时间实时判断。 */
    private String effectiveTier(User user, LocalDateTime now) {
        return "VIP".equalsIgnoreCase(user.getTier())
                && user.getVipExpireAt() != null
                && user.getVipExpireAt().isAfter(now) ? "VIP" : "FREE";
    }

    /** 解析并校验 accessToken，非法 / 过期统一抛 401 */
    private Long parseUserId(String accessToken) {
        try {
            Claims claims = jwtUtil.parse(accessToken);
            return Long.valueOf(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            throw new ApiException(401, "accessToken 无效或已过期");
        }
    }

    /** 签发双 token：access = JWT；refresh = 随机串写 Redis（TTL 同 refresh-ttl-ms） */
    private LoginResponse issueTokens(User user) {
        String accessToken = jwtUtil.createAccessToken(user.getId(), user.getUsername());

        String refreshToken = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(
                REFRESH_KEY_PREFIX + refreshToken,
                String.valueOf(user.getId()),
                Duration.ofMillis(jwtProps.getRefreshTtlMs()));

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }
}
