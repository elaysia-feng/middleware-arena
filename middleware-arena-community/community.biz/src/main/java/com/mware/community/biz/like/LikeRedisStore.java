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
 * Redis 点赞写模型。
 * <p>
 * 负责：用户点赞状态、帖子实时点赞数、单调 version、Redis Stream Outbox、热点统计。
 * 用户状态不使用 Bitmap：userId 可能是 Snowflake 大整数，直接作为 bit offset 会形成巨大的稀疏位图；
 * 因此按 userId 对 Hash 再分片，避免单个点赞状态 Key 无限膨胀。
 */
@Component
public class LikeRedisStore {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    /**
     * 点赞状态变更脚本。
     * <p>
     * KEYS：用户状态 Hash / 点赞数 / version / Stream Outbox。
     * 一次 Lua 内完成状态、计数、version 和事件写入，避免“Redis 已成功但事件没写入”的中间状态。
     */
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

    /**
     * 热榜统计脚本：eventId 去重和 ZINCRBY 必须一起完成，避免 MQ 重复投递造成榜单重复加分。
     */
    private static final DefaultRedisScript<Long> STATISTICS_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('SETNX', KEYS[1], '1') == 0 then return 0 end
            redis.call('EXPIRE', KEYS[1], tonumber(ARGV[3]))
            redis.call('ZINCRBY', KEYS[2], ARGV[1], ARGV[2])
            redis.call('EXPIRE', KEYS[2], tonumber(ARGV[3]))
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    /** Redis Stream 分区数：不同 postId 分散到多个 Stream，避免一个事件 Stream 形成 BigKey。 */
    @Value("${community.like.redis-partitions:64}")
    private int redisPartitions;

    /** 单帖子用户点赞状态再拆成多个 Hash，避免热门帖子形成超大 Hash。 */
    @Value("${community.like.state-shards:256}")
    private int stateShards;

    /** RabbitMQ 长时间故障时的 Stream 硬上限，达到后拒绝新的状态变化，保护 Redis 内存。 */
    @Value("${community.like.stream-hard-limit:500000}")
    private long streamHardLimit;

    /** 热榜去重 Key 和日榜的 TTL。 */
    @Value("${community.like.statistics-ttl-seconds:172800}")
    private long statisticsTtlSeconds;

    public LikeRedisStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 将某个用户对某个帖子的点赞状态设置为目标状态。
     */
    public MutationResult setLiked(Long postId, Long userId, boolean liked) {
        //   1. 校验业务参数和分片配置，防止错误配置导致取模异常或 Stream 无保护增长。
        validate(postId, userId);
        if (redisPartitions <= 0 || stateShards <= 0 || streamHardLimit <= 0) {
            throw new IllegalStateException("community.like Redis partition/shard/stream limits must be positive");
        }

        //   2. eventId 在状态真正发生变化时写入 Stream，后续贯穿 RabbitMQ 和消费幂等链路。
        String eventId = UUID.randomUUID().toString().replace("-", "");

        //   3. 四个 Redis Key 使用相同 Cluster HashTag，保证 Lua 在 Redis Cluster 下仍落在同一 slot。
        @SuppressWarnings("unchecked")
        List<Object> result = redisTemplate.execute(
                SET_STATE_SCRIPT,
                List.of(
                        stateKey(postId, userId),
                        countKey(postId),
                        versionKey(postId),
                        streamKey(postId)
                ),
                String.valueOf(userId),
                liked ? "1" : "0",
                eventId,
                String.valueOf(postId),
                String.valueOf(Instant.now().toEpochMilli()),
                String.valueOf(streamHardLimit));

        //   4. Lua 固定返回：目标状态 / 最新点赞数 / version / 是否真正发生状态变化。
        if (result == null || result.size() < 4) {
            throw new IllegalStateException("Redis like Lua returned invalid result");
        }

        return new MutationResult(
                asLong(result.get(0)) == 1,
                asLong(result.get(1)),
                asLong(result.get(2)),
                asLong(result.get(3)) == 1);
    }

    /**
     * 查询当前用户是否已经点赞。
     */
    public boolean isLiked(Long postId, Long userId) {
        validate(postId, userId);

        // 用户状态按 userId % stateShards 路由到对应 Hash，只查询一个分片。
        return Boolean.TRUE.equals(redisTemplate.opsForHash()
                .hasKey(stateKey(postId, userId), String.valueOf(userId)));
    }

    /**
     * 查询帖子实时点赞数。
     */
    public long likeCount(Long postId) {
        if (postId == null || postId <= 0) {
            throw new IllegalArgumentException("postId must be positive");
        }

        String value = redisTemplate.opsForValue().get(countKey(postId));
        return value == null ? 0L : Long.parseLong(value);
    }

    /**
     * 生成所有 Redis Stream 分区 Key。
     * <p>
     * 这是标准的“范围 → Key 列表”转换，直接使用 IntStream，不手写 for + add。
     */
    public List<String> eventStreamKeys() {
        if (redisPartitions <= 0) {
            throw new IllegalStateException("community.like.redis-partitions must be positive");
        }

        return IntStream.range(0, redisPartitions)
                .mapToObj(this::streamKeyByPartition)
                .toList();
    }

    /**
     * Redis Stream MapRecord → 点赞事件 DTO。
     * <p>
     * 这里只转换单个对象，不是集合转换，因此 Builder 比 Stream 更直接；
     * 集合批量转换时再使用 records.stream().map(this::toEvent).toList()。
     */
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

    /**
     * 将点赞/取消点赞事件计入当天热榜。
     */
    public boolean applyStatistics(LikeEvent event) {
        //   1. 热榜按点赞事件所属 postId 分区，保持 Redis Cluster 同槽操作。
        int partition = partition(event.getPostId());
        String tag = tag(partition);

        //   2. 按业务时区生成日榜 Key，避免一个永久 ZSet 无限增长。
        String day = Instant.ofEpochMilli(event.getTimestamp())
                .atZone(BUSINESS_ZONE)
                .toLocalDate()
                .format(DAY);

        String doneKey = "like:" + tag + ":stats:done:" + event.getEventId();
        String hotKey = "like:" + tag + ":hot:" + day;

        //   3. Lua 内先用 eventId SETNX 去重，再修改 ZSet，保证 MQ 重复投递不会重复累计。
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

    /** postId → Redis 事件分区。 */
    private int partition(Long postId) {
        return Math.floorMod(postId, redisPartitions);
    }

    /**
     * Redis Cluster HashTag，同一个分区相关 Key 都包含相同的 {like:pN}。
     */
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

    /**
     * Lua 状态变更结果。
     *
     * @param liked     最终点赞状态
     * @param likeCount 当前实时点赞数
     * @param version   本次状态变更 version；幂等未变化时为 0
     * @param changed   本次请求是否真的改变了状态
     */
    public record MutationResult(boolean liked, long likeCount, long version, boolean changed) {
    }
}
