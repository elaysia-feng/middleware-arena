package com.mware.community.biz.favorite;

import com.mware.community.dto.message.FavoriteEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.stream.IntStream;

/**
 * Redis 收藏写模型。
 * <p>
 * 用户状态使用分片 Hash，帖子收藏数和 version 使用 String，待投递事件使用 Stream。
 * 四类 Key 带相同的 Redis Cluster HashTag，因此可以在一个 Lua 中原子修改。
 */
@Component
public class FavoriteRedisStore {

    /**
     * KEYS：用户状态 Hash、帖子收藏数、version、Stream Outbox。
     * ARGV：userId、目标状态、eventId、postId、时间戳、Stream 硬上限。
     */
    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> SET_STATE_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('HEXISTS', KEYS[1], ARGV[1])
            local desired = tonumber(ARGV[2])
            local count = tonumber(redis.call('GET', KEYS[2]) or '0')

            if current == desired then
                return {desired, count, 0, 0}
            end
            if redis.call('XLEN', KEYS[4]) >= tonumber(ARGV[6]) then
                return redis.error_reply('FAVORITE_STREAM_BACKLOG_FULL')
            end

            local version = redis.call('INCR', KEYS[3])
            if desired == 1 then
                redis.call('HSET', KEYS[1], ARGV[1], '1')
                count = redis.call('INCRBY', KEYS[2], 1)
            else
                redis.call('HDEL', KEYS[1], ARGV[1])
                count = redis.call('INCRBY', KEYS[2], -1)
            end

            redis.call('XADD', KEYS[4], '*',
                'eventId', ARGV[3], 'postId', ARGV[4], 'userId', ARGV[1],
                'favorited', tostring(desired), 'version', tostring(version),
                'favoriteCount', tostring(count), 'timestamp', ARGV[5])
            return {desired, count, version, 1}
            """, List.class);

    private final StringRedisTemplate redisTemplate;

    /** 不同帖子按 postId 分散到多个事件 Stream，避免形成单个全局大 Stream。 */
    @Value("${community.favorite.redis-partitions:64}")
    private int redisPartitions;

    /** 单帖用户状态继续按 userId 分片，避免热门帖子形成单个超大 Hash。 */
    @Value("${community.favorite.state-shards:256}")
    private int stateShards;

    /** RabbitMQ 长时间不可用时拒绝继续堆积，保护 Redis 内存。 */
    @Value("${community.favorite.stream-hard-limit:500000}")
    private long streamHardLimit;

    public FavoriteRedisStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 将收藏状态设置为目标值；重复 PUT/DELETE 不反转状态，也不重复修改计数。 */
    public void setFavorited(Long postId, Long userId, boolean favorited) {
        validate(postId, userId);
        if (redisPartitions <= 0 || stateShards <= 0 || streamHardLimit <= 0) {
            throw new IllegalStateException("community.favorite Redis configuration must be positive");
        }

        // 状态真正变化时，Lua 同时写入事件；请求线程不直接访问 MySQL 或 RabbitMQ。
        @SuppressWarnings("unchecked")
        List<Object> result = redisTemplate.execute(
                SET_STATE_SCRIPT,
                List.of(stateKey(postId, userId), countKey(postId), versionKey(postId), streamKey(postId)),
                String.valueOf(userId),
                favorited ? "1" : "0",
                UUID.randomUUID().toString().replace("-", ""),
                String.valueOf(postId),
                String.valueOf(Instant.now().toEpochMilli()),
                String.valueOf(streamHardLimit));

        if (result == null || result.size() < 4) {
            throw new IllegalStateException("Redis favorite Lua returned invalid result");
        }
    }

    /** 查询当前用户是否收藏该帖子，只读取 userId 所在的状态 Hash 分片。 */
    public boolean isFavorited(Long postId, Long userId) {
        validate(postId, userId);
        return Boolean.TRUE.equals(redisTemplate.opsForHash()
                .hasKey(stateKey(postId, userId), String.valueOf(userId)));
    }

    /** 查询 Redis 中的实时收藏数；MySQL 中的 favorite_count 为异步落库值。 */
    public long favoriteCount(Long postId) {
        if (postId == null || postId <= 0) {
            throw new IllegalArgumentException("postId must be positive");
        }
        String count = redisTemplate.opsForValue().get(countKey(postId));
        return count == null ? 0L : Long.parseLong(count);
    }

    /** 返回调度器需要轮询的全部收藏事件 Stream Key。 */
    public List<String> eventStreamKeys() {
        if (redisPartitions <= 0) {
            throw new IllegalStateException("community.favorite.redis-partitions must be positive");
        }
        return IntStream.range(0, redisPartitions).mapToObj(this::streamKeyByPartition).toList();
    }

    /** 帖子删除后清理该帖的收藏状态、计数和版本；共享分区 Stream 不在这里删除。 */
    public void deletePostState(Long postId) {
        if (postId == null || postId <= 0 || stateShards <= 0) {
            return;
        }
        List<String> keys = new ArrayList<>(stateShards + 2);
        keys.add(countKey(postId));
        keys.add(versionKey(postId));
        for (int shard = 0; shard < stateShards; shard++) {
            keys.add("favorite:" + tag(partition(postId)) + ":post:" + postId + ":user-state:" + shard);
        }
        redisTemplate.delete(keys);
    }

    /** 将 Stream 字段转换为 RabbitMQ 使用的收藏事件 DTO。 */
    public FavoriteEvent toEvent(MapRecord<String, Object, Object> record) {
        Map<Object, Object> value = record.getValue();
        return FavoriteEvent.builder()
                .eventId(text(value, "eventId"))
                .postId(Long.valueOf(text(value, "postId")))
                .userId(Long.valueOf(text(value, "userId")))
                .favorited("1".equals(text(value, "favorited")))
                .version(Long.valueOf(text(value, "version")))
                .favoriteCount(Long.valueOf(text(value, "favoriteCount")))
                .timestamp(Long.valueOf(text(value, "timestamp")))
                .build();
    }

    private String streamKey(Long postId) {
        return streamKeyByPartition(partition(postId));
    }

    private String streamKeyByPartition(int partition) {
        return "favorite:" + tag(partition) + ":post-events";
    }

    private String countKey(Long postId) {
        return "favorite:" + tag(partition(postId)) + ":post:" + postId + ":count";
    }

    private String versionKey(Long postId) {
        return "favorite:" + tag(partition(postId)) + ":post:" + postId + ":ver";
    }

    private String stateKey(Long postId, Long userId) {
        // Snowflake userId 不适合作为 Bitmap offset，因此用分片 Hash 保存状态。
        int shard = Math.floorMod(userId, stateShards);
        return "favorite:" + tag(partition(postId)) + ":post:" + postId + ":user-state:" + shard;
    }

    private int partition(Long postId) {
        return Math.floorMod(postId, redisPartitions);
    }

    private String tag(int partition) {
        return "{p" + partition + "}";
    }

    private static String text(Map<Object, Object> value, String key) {
        Object raw = value.get(key);
        if (raw == null) {
            throw new IllegalStateException("missing favorite stream field: " + key);
        }
        return String.valueOf(raw);
    }

    private static void validate(Long postId, Long userId) {
        if (postId == null || postId <= 0 || userId == null || userId <= 0) {
            throw new IllegalArgumentException("postId and userId must be positive");
        }
    }
}
