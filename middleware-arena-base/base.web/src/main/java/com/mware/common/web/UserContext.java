package com.mware.common.web;

import com.alibaba.ttl.TransmittableThreadLocal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前请求用户上下文：由 {@link AuthHeaderInterceptor} 从网关透传的
 * X-User-Id / X-Username Header 填充，业务代码通过静态方法直接访问。
 * <p>
 * 使用 {@link TransmittableThreadLocal}，异步场景（线程池 / 异步任务）下用户上下文
 * 可透传到子线程，避免线程池内丢失当前用户身份。
 * <p>
 * 注意：TTL 透传需要满足其一 —— JVM 启动加 {@code -javaagent:transmittable-thread-local.jar}，
 * 或提交任务时用 {@code TtlExecutors} / {@code TtlRunnable} 包装；否则等同普通 ThreadLocal。
 * 请求结束由拦截器 {@link AuthHeaderInterceptor#afterCompletion} 调用 {@link #clear()} 清理。
 */
public final class UserContext {

    private static final TransmittableThreadLocal<CurrentUser> HOLDER = new TransmittableThreadLocal<>();

    private UserContext() {
    }

    /** 当前登录用户，未登录（未经过网关）时为 null */
    public static CurrentUser get() {
        return HOLDER.get();
    }

    /** 当前用户 ID；未登录返回 null */
    public static Long getUserId() {
        CurrentUser user = HOLDER.get();
        return user == null ? null : user.getUserId();
    }

    /** 当前用户名；未登录返回 null */
    public static String getUsername() {
        CurrentUser user = HOLDER.get();
        return user == null ? null : user.getUsername();
    }

    static void set(Long userId, String username) {
        HOLDER.set(new CurrentUser(userId, username));
    }

    static void clear() {
        HOLDER.remove();
    }

    /** 不可变的当前用户快照 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CurrentUser {
        private Long userId;
        private String username;
    }
}
