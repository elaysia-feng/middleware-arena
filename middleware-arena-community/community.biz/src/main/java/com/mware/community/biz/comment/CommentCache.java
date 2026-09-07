package com.mware.community.biz.comment;

import com.mware.community.dto.response.CommentResponse;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 评论列表缓存工具（v1 最小化方案）。
 * <p>
 * 设计决策（v1 范围内，骨架阶段够用）：
 * <ul>
 *   <li><b>只缓存前 3 页</b>（{@link #MAX_CACHED_PAGE}）：深分页本就该慢，避免打爆 Redis 内存</li>
 *   <li><b>TTL 固定 5 分钟</b>（{@link #TTL}）：不做热点分级，热点识别后续 v2 再加</li>
 *   <li><b>提交后失效</b>：评论增删提交后清理分页缓存，TTL 作为清理失败的兜底</li>
 *   <li><b>不加 Caffeine</b>：单层 Redis 就够，省一层本地缓存复杂度（v2 按需再加）</li>
 *   <li><b>Fail-open</b>：Redis 异常降级到 DB，不让缓存故障拖垮主链路</li>
 * </ul>
 * <p>
 * key 格式：{@code comment:list:p{postId}:p{page}:s{size}}
 * <p>
 * 仅缓存帖子评论分页（pageComments），回复分页（pageReplies）由调用方按相同方式接入——目前未启用。
 */
@Component
public class CommentCache {

    /** 缓存 key 前缀 */
    public static final String KEY_PREFIX = "comment:list:";

    /** 缓存过期时间：5 分钟（v1 固定值，不做热点分级） */
    public static final Duration TTL = Duration.ofMinutes(5);

    /** 仅缓存前 N 页：第 N+1 页直接走 MySQL，避免深分页打爆 Redis */
    public static final int MAX_CACHED_PAGE = 3;

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 注入评论缓存使用的 Redis 客户端。
     * @param redisTemplate Redis 客户端
     */
    public CommentCache(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 构造缓存 key：{@code comment:list:p{postId}:p{page}:s{size}}。
     * <p>
     * 把 page/size 拼进 key 而非 hash tag，避免大 key；同时不同分页参数互不污染。
     * @param postId 帖子 ID
     * @param page 页码
     * @param size 每页数量
     * @return 分页缓存键
     */
    public String buildKey(Long postId, int page, int size) {
        return KEY_PREFIX + "p" + postId + ":p" + page + ":s" + size;
    }

    /**
     * 单次读取缓存，避免 EXISTS 与 GET 之间过期造成误判；异常由业务侧降级查库。
     * @param postId 帖子 ID
     * @param page 页码
     * @param size 每页数量
     * @return 命中的列表（可以为空）；未命中或类型不匹配时返回 null
     */
    public List<CommentResponse> get(Long postId, int page, int size) {
        String key = buildKey(postId, page, size);
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached instanceof List<?> list) {
            // GenericJackson2JsonRedisSerializer 反序列化时会保留泛型擦除后的原始类型
            // 这里做一次类型校验，不通过则降级
            return list.stream()
                    .map(o -> (CommentResponse) o)
                    .toList();
        }
        return null;
    }

    /**
     * 写入缓存，包括空结果，减少重复空查询对数据库的压力；异常由调用方处理。
     * @param postId 帖子 ID
     * @param page 页码
     * @param size 每页数量
     * @param value 待缓存的列表，null 不写入
     */
    public void put(Long postId, int page, int size, List<CommentResponse> value) {
        if (value == null) {
            return;
        }
        String key = buildKey(postId, page, size);
        redisTemplate.opsForValue().set(key, value, TTL);
    }

    /**
     * 判断当前 (page, size) 是否启用缓存。
     * <p>
     * 当前规则：{@code page <= MAX_CACHED_PAGE}。深分页直接走 DB。
     * @param page 页码
     * @return 是否缓存该页
     */
    public boolean isCacheable(int page) {
        return page >= 1 && page <= MAX_CACHED_PAGE;
    }

    /**
     * 探测缓存 key 是否存在，仅用于诊断；业务读取应使用 get 的返回值判断是否命中。
     * <p>
     * 异常被吞掉，返回 false（=未命中），主流程降级到 DB。
     * @param postId 帖子 ID
     * @param page 页码
     * @param size 每页数量
     * @return 缓存键是否存在
     */
    public boolean hasKey(Long postId, int page, int size) {
        try {
            String key = buildKey(postId, page, size);
            Boolean exists = redisTemplate.hasKey(key);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 评论增删后按帖子扫描并删除分页缓存，失败由 TTL 兜底。
     * @param postId 帖子 ID
     */
    public void evictPost(Long postId) {
        ScanOptions options = ScanOptions.scanOptions()
                .match(KEY_PREFIX + "p" + postId + ":*")
                .count(100)
                .build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            cursor.forEachRemaining(redisTemplate::delete);
        } catch (Exception ignored) {
            // 缓存清理失败时主业务仍成功，最多由 TTL 自然修复。
        }
    }
}
