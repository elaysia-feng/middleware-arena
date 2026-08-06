package com.mware.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 全局认证过滤器（骨架占位，先放行所有请求）。
 * <p>
 * TODO[双 token 登录]：
 *   - 白名单放行：/auth/login、/auth/refresh、/actuator/**
 *   - 其余请求校验请求头 Authorization: Bearer &lt;accessToken&gt;
 *   - 校验通过后把 uid / username 透传到下游 Header（如 X-User-Id / X-Username）
 *   - 校验失败返回 401
 */
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        // TODO：实现 accessToken 校验逻辑
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
