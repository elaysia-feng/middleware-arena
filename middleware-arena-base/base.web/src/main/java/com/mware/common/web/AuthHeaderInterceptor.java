package com.mware.common.web;

import com.mware.common.jwt.InternalAuthProperties;
import com.mware.common.jwt.InternalAuthSigner;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 从网关透传的 X-User-Id / X-Username Header 填充 {@link UserContext}。
 * <p>
 * 依赖链：网关 {@code AuthGlobalFilter} 校验 JWT 后透传这两个 Header，
 * 下游服务据此识别当前用户，无需再解密 JWT。
 * <p>
 * 防直连伪造（fail-closed）：X-User-Id 身份头<b>只有携带合法 HMAC 签名</b>
 * （X-Timestamp / X-Sign 由 {@link InternalAuthSigner} 计算）时才会被信任。
 * 网关 {@code AuthGlobalFilter} 会先剥离客户端可能注入的 X-* 头再写入自己签名的值，
 * 因此只要网关 + 下游都配置了相同的 {@code ma.internal-auth.secret}，
 * 绕过网关直连服务端口伪造的 X-User-Id 会被拦截并 401。
 * <p>
 * 注意：无身份头（X-User-Id 为空）的请求等价于未登录（UserContext.get() 为 null），
 * 由业务自行决定是否拒绝（如登录白名单路径 / 健康检查）。</p>
 */
public class AuthHeaderInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthHeaderInterceptor.class);

    private final InternalAuthProperties props;

    public AuthHeaderInterceptor(InternalAuthProperties props) {
        this.props = props;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        String userIdHeader = request.getHeader("X-User-Id");
        boolean hasIdentity = StringUtils.hasText(userIdHeader);

        if (!hasIdentity) {
            // 无身份头（网关白名单转发 / 直连无头请求）→ 等同未登录，UserContext 为 null
            return true;
        }

        // 带了身份头 → 必须能验签才信任，任何一步失败都拒绝（fail-closed）
        String secret = props.getSecret();
        if (!StringUtils.hasText(secret)) {
            // 关键加固：身份头可信的前提是能验签；未配置密钥无法验签，视为伪造拒绝。
            // 强制网关与下游都配置 ma.internal-auth.secret，杜绝"漏配即信任客户端头"。
            log.warn("请求携带 X-User-Id 身份头但未配置 ma.internal-auth.secret，已拒绝身份透传");
            return reject(response);
        }

        String username = request.getHeader("X-Username");
        String timestamp = request.getHeader("X-Timestamp");
        String sign = request.getHeader("X-Sign");

        // 防重放：时间戳超出允许窗口（默认 5 分钟）拒绝
        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            return reject(response);
        }
        if (Math.abs(System.currentTimeMillis() - ts) > props.getReplayWindowMs()) {
            return reject(response);
        }
        if (!InternalAuthSigner.verify(secret, userIdHeader, username, timestamp, sign)) {
            return reject(response);
        }

        fillUserContext(request);
        return true;
    }

    private void fillUserContext(HttpServletRequest request) {
        String userId = request.getHeader("X-User-Id");
        String username = request.getHeader("X-Username");

        if (StringUtils.hasText(userId)) {
            Long id = null;
            try {
                id = Long.parseLong(userId);
            } catch (NumberFormatException ignored) {
                // 非法用户 ID 不填充，等同未登录
            }
            if (id != null) {
                UserContext.set(id, username);
            }
        }
    }

    /** 验签失败：返回 401 并中断请求，避免落入 Controller */
    private boolean reject(HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        try {
            response.getWriter().write("{\"code\":401,\"msg\":\"internal auth signature invalid\"}");
        } catch (Exception ignored) {
            // 响应流异常忽略，请求已按 401 处理
        }
        return false;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler, Exception ex) {
        // 线程复用前必须清理，否则下一个请求会读到上一个用户的身份
        UserContext.clear();
    }
}
