package com.mware.community.biz.like;

import com.mware.community.dto.message.LikeEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Redis 点赞写模型：Cluster 同槽 Lua + 用户 Hash 分片 + Stream Outbox。
 * <p>
 * 用户状态不用 Bitmap：userId 可能是 Snowflake 大整数，直接作为 bit offset 会形成巨大的稀疏位图。
 */
@Component
public class LikeRedisStore {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> SET_STATE_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('HEXISTS', KEYS[1], ARGV[1])
            local desired = tonumber(ARGV[2])
            local count = tonumber(redis.call('GET', KEYS[2]) or '0')

            -- PUT/DELETE 是幂等目标状态：已经是目标状态时，即使 MQ 堆积也直接成功。
            if current == desired then
                return {desired, count, 0, 0}
            end

            -- Stream 是可靠 Outbox，不能 MAXLEN 裁掉未投递事件；达到硬阈值时在任何状态修改前拒绝写入。
            if redis.call('XLEN', KEYS[4]) >= tonumber(ARGV[6]) then
                return redis.error_reply('LIKE_STREAM_BACKLOG_FULL')
            end

            local version = redis.call('INCR', KEYS[3])
            local delta = 0
            local action = ''
            if desired == 1 then
                redis.call('HSET', KEYS[1], ARGV[1], '1')
                delta = 1
                action = 'LIKE'
            else
                redis.call('HDEL', KEYS[1], ARGV[1])
                delta = -1
                action = 'UNLIKE'
            end

            count = redis.call('INCRBY', KEYS[2], delta)
            redis.call('XADD', KEYS[4], '*',
                'eventId', ARGV[3], 'postId', ARGV[4], 'userId', ARGV[1],
                'action', action, 'liked', tostring(desired), 'delta', tostring(delta),
                'version', tostring(version), 'likeCount', tostring(count), 'timestamp', ARGV[5])
            return {desired, count, version, 1}
            """, List.class);

    private static final DefaultRedisScript<Long> STATISTICS_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('SETNX', KEYS[1], '1') == 0 then return 0 end
            redis.call('EXPIRE', KEYS[1], tonumber(ARGV[3]))
            redis.call('ZINCRBY', KEYS[2], ARGV[1], ARGV[2])
            redis.call('EXPIRE', KEYS[2], tonumber(ARGV[3]))
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    @Value("${community.like.redis-partitions:64}") private int redisPartitions;
    @Value("${community.like.state-shards:256}") private int stateShards;
    @Value("${community.like.stream-hard-limit:500000}") private long streamHardLimit;
    @Value("${community.like.statistics-ttl-seconds:172800}") private long statisticsTtlSeconds;

    public LikeRedisStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public MutationResult setLiked(Long postId, Long userId, boolean liked) {
        validate(postId, userId);
        if (redisPartitions <= 0 || stateShards <= 0 || streamHardLimit <= 0) {
            throw new IllegalStateException("community.like Redis partition/shard/stream limits must be positive");
        }

        String eventId = UUID.randomUUID().toString().replace("-", "");
        @SuppressWarnings("unchecked")
        List<Object> result = redisTemplate.execute(
                SET_STATE_SCRIPT,
                List.of(stateKey(postId, userId), countKey(postId), versionKey(postId), streamKey(postId)),
                String.valueOf(userId),
                liked ? "1" : "0",
                eventId,
                String.valueOf(postId),
                String.valueOf(Instant.now().toEpochMilli()),
                String.valueOf(streamHardLimit));
        if (result == null || result.size() < 4) {
            throw new IllegalStateException("Redis like Lua returned invalid result");
        }
        return new MutationResult(
                asLong(result.get(0)) == 1,
                asLong(result.get(1)),
                asLong(result.get(2)),
                asLong(result.get(3)) == 1);
    }

    public boolean isLiked(Long postId, Long userId) {
        validate(postId, userId);
        return Boolean.TRUE.equals(redisTemplate.opsForHash()
                .hasKey(stateKey(postId, userId), String.valueOf(userId)));
    }

    public long likeCount(Long postId) {
        if (postId == null || postId <= 0) {
            throw new IllegalArgumentException("postId must be positive");
        }
        String value = redisTemplate.opsForValue().get(countKey(postId));
        return value == null ? 0L : Long.parseLong(value);
    }

    public List<String> eventStreamKeys() {
        if (redisPartitions <= 0) {
            throw new IllegalStateException("community.like.redis-partitions must be positive");
        }
        return IntStream.range(0, redisPartitions).mapToObj(this::streamKeyByPartition).toList();
    }

    public LikeEvent toEvent(MapRecord<String, Object, Object> record) {
        Map<Object, Object> value = record.getValue();
        return LikeEvent.builder()
                .eventId(text(value, "eventId"))
                .postId(Long.valueOf(text(value, "postId")))
                .userId(Long.valueOf(text(value, "userId")))
                .action(text(value, "action"))
                .liked("1".equals(text(value, "liked")))
                .delta(Integer.valueOf(text(value, "delta")))
                .version(Long.valueOf(text(value, "version")))
                .likeCount(Long.valueOf(text(value, "likeCount")))
                .timestamp(Long.valueOf(text(value, "timestamp")))
                .build();
    }

    public boolean applyStatistics(LikeEvent event) {
        int partition = partition(event.getPostId());
        String tag = tag(partition);
        String day = Instant.ofEpochMilli(event.getTimestamp())
                .atZone(BUSINESS_ZONE)
                .toLocalDate()
                .format(DAY);
        String doneKey = "like:" + tag + ":stats:done:" + event.getEventId();
        String hotKey = "like:" + tag + ":hot:" + day;
        Long changed = redisTemplate.execute(
                STATISTICS_SCRIPT,
                List.of(doneKey, hotKey),
                String.valueOf(event.getDelta()),
                String.valueOf(event.getPostId()),
                String.valueOf(statisticsTtlSeconds));
        return Long.valueOf(1L).equals(changed);
    }

    public String streamKey(Long postId) {
        return streamKeyByPartition(partition(postId));
    }

    private String streamKeyByPartition(int partition) {
        return "like:" + tag(partition) + ":events";
    }

    private String countKey(Long postId) {
        return "like:" + tag(partition(postId)) + ":post:" + postId + ":count";
    }

    private String versionKey(Long postId) {
        return "like:" + tag(partition(postId)) + ":post:" + postId + ":version";
    }

    private String stateKey(Long postId, Long userId) {
        int shard = Math.floorMod(userId, stateShards);
        return "like:" + tag(partition(postId)) + ":post:" + postId + ":state:" + shard;
    }

    private int partition(Long postId) {
        return Math.floorMod(postId, redisPartitions);
    }

    private String tag(int partition) {
        return "{like:p" + partition + "}";
    }

    private static String text(Map<Object, Object> value, String key) {
        Object raw = value.get(key);
        if (raw == null) {
            throw new IllegalStateException("missing stream field: " + key);
        }
        return String.valueOf(raw);
    }

    private static long asLong(Object value) {
        return value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value));
    }

    private static void validate(Long postId, Long userId) {
        if (postId == null || postId <= 0) {
            throw new IllegalArgumentException("postId must be positive");
        }
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
    }

    public record MutationResult(boolean liked, long likeCount, long version, boolean changed) {}
}
