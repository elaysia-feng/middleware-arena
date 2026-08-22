package com.mware.gateway.filter;

import com.mware.common.jwt.InternalAuthSigner;
import com.mware.common.jwt.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 全局认证过滤器：白名单放行，其余请求校验 Authorization: Bearer &lt;accessToken&gt;。
 * <p>
 * 校验通过后把 uid / username 透传到下游 Header（X-User-Id / X-Username），
 * 并追加 HMAC 签名（X-Timestamp / X-Sign）防止下游被直连伪造身份。
 */
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    /** 无需登录即可访问的路径（登录 / 刷新令牌 / 健康检查） */
    private static final List<PathPattern> WHITELIST = List.of(
            PathPatternParser.defaultInstance.parse("/auth/login"),
            PathPatternParser.defaultInstance.parse("/auth/register"),
            PathPatternParser.defaultInstance.parse("/auth/refresh"),
            PathPatternParser.defaultInstance.parse("/actuator/**")
    );

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

    /** 与下游共享的验签密钥，生产环境通过环境变量覆盖。 */
    @Value("${ma.internal-auth.secret:middleware-arena-internal-token}")
    private String internalAuthSecret;

    public AuthGlobalFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // CORS 预检请求直接放行，避免 401 挡住浏览器跨域探测
        if (HttpMethod.OPTIONS.equals(request.getMethod())) {
            return chain.filter(exchange);
        }

        // 白名单路径不校验（PathPattern 按规范化的路径容器匹配，防止 dot-segment 绕过）
        PathContainer path = request.getPath().pathWithinApplication();
        if (WHITELIST.stream().anyMatch(p -> p.matches(path))) {
            return forwardWithIdentity(exchange, chain, null, null);
        }

        // 1. 取 Authorization header
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange, "缺少或非法的 Authorization 头");
        }
        String token = authHeader.substring(BEARER_PREFIX.length());

        // 2. 解析校验 token（非法 / 过期抛 JwtException）
        final Claims claims;
        try {
            claims = jwtUtil.parse(token);
        } catch (JwtException | IllegalArgumentException e) {
            return unauthorized(exchange, "token 无效或已过期");
        }

        // 3. 透传身份到下游：subject 是 userId，username 在自定义 claim
        String userId = claims.getSubject();
        String username = claims.get("username", String.class);
        return forwardWithIdentity(exchange, chain, userId, username);
    }

    /** 构造带身份与签名的转发请求（签名见 InternalAuthSigner，下游验签防直连伪造） */
    private Mono<Void> forwardWithIdentity(ServerWebExchange exchange, GatewayFilterChain chain,
                                           String userId, String username) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String sign = InternalAuthSigner.sign(internalAuthSecret, userId, username, timestamp);

        // 必须先剥离客户端可能注入的 X-* 身份头再写自己的值：
        // mutate().header() 是追加语义，若不剥离，客户端伪造的 X-User-Id 会排在前面，
        // 下游 getHeader 读到第一个（伪造值），要么验签失败拒绝合法请求，要么绕过验签伪造身份。
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove("X-User-Id");
                    headers.remove("X-Username");
                    headers.remove("X-Timestamp");
                    headers.remove("X-Sign");
                    headers.add("X-User-Id", userId == null ? "" : userId);
                    headers.add("X-Username", username == null ? "" : username);
                    headers.add("X-Timestamp", timestamp);
                    headers.add("X-Sign", sign);
                })
                .build();

        return chain.filter(exchange.mutate().request(mutated).build());
    }

    /** 响应式写 401 JSON（不能抛异常，否则会被框架当 500 处理） */
    private Mono<Void> unauthorized(ServerWebExchange exchange, String msg) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = ("{\"code\":401,\"msg\":\"" + msg + "\"}").getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // 负数，确保在其它 GlobalFilter 之前执行
        return -100;
    }
}
