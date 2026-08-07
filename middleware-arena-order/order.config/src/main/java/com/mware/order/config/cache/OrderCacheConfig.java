package com.mware.order.config.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mware.order.domain.Order;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 订单本地缓存配置：Caffeine 一级缓存，配合 Redis Cache-Aside（本地 → Redis → DB）。
 * <p>
 * 用法：在查询方法上加 {@code @Cacheable("order")}，或用注入的 {@link CacheManager} 手动读写。
 */
@Configuration
@EnableCaching
public class OrderCacheConfig {

    /** Caffeine 缓存管理器：order 命名空间（订单详情缓存） */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("order");
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofMinutes(30)));
        return cacheManager;
    }

    /** Caffeine 原生缓存：getOrder 手动读写（getIfPresent/put）使用 */
    @Bean
    public Cache<Long, Order> orderCache() {
        return Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofMinutes(30))
                .build();
    }
}
