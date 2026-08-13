package com.mware.runner.biz.scheduler.impl;

import com.mware.runner.biz.config.ResourceBusyException;
import com.mware.runner.biz.config.ExperimentType;
import com.mware.runner.biz.config.ResourceTier;
import com.mware.runner.biz.config.RunnerProperties;
import com.mware.runner.biz.config.RunnerRedisKeys;
import com.mware.runner.biz.scheduler.ResourceScheduler;
import com.mware.runner.dto.RunnerTaskMessage;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * 平台级资源调度实现：槽位 + CPU + 内存三者同时满足才放行。
 */
@Component
@Slf4j
public class ResourceSchedulerImpl implements ResourceScheduler {

    /** 轮询等待间隔（ms） */
    private static final long POLL_MS = 500;

    private final RunnerProperties properties;
    private final StringRedisTemplate redisTemplate;

    /** LOCAL 模式下的资源状态，统一由 localBudgetLock 保护。 */
    private final Object localBudgetLock = new Object();
    private final Map<Long, Reservation> held = new HashMap<>();
    private final Deque<Long> vipWaitingTasks = new ArrayDeque<>();
    private final Deque<Long> freeWaitingTasks = new ArrayDeque<>();
    private int reservedSlots;
    private long reservedCpuUnits;
    private long reservedMemoryMb;
    private final Map<String, Integer> runningTasksByUser = new HashMap<>();

    private record Reservation(ResourceTier tier, String userKey, long cpuUnits, long memoryMb) {
    }

    /**
     * REDIS 模式原子获取资源：
     * 1. 清理过期等待任务并校验当前任务是本等级队首。
     * 2. 校验平台总资源、单用户并发上限和 VIP 优先规则。
     * 3. 一次性登记平台占用、用户占用和任务 Reservation。
     */
    private static final DefaultRedisScript<Long> ACQUIRE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[2]) == 1 then
                return 1
            end

            -- 先清理异常退出实例留下的过期等待项，再判断队首和 VIP 是否仍在等待。
            redis.call('ZREMRANGEBYSCORE', KEYS[3], '-inf', ARGV[11])
            redis.call('ZREMRANGEBYSCORE', KEYS[4], '-inf', ARGV[11])
            redis.call('ZADD', KEYS[3], 'NX', ARGV[7], ARGV[8])
            local rank = redis.call('ZRANK', KEYS[3], ARGV[8])
            if rank == false or rank ~= 0 then
                return 0
            end

            local tier = ARGV[3]
            local slots = tonumber(redis.call('HGET', KEYS[1], 'slots') or '0')
            local cpuUnits = tonumber(redis.call('HGET', KEYS[1], 'cpuUnits') or '0')
            local memoryMb = tonumber(redis.call('HGET', KEYS[1], 'memoryMb') or '0')
            local userField = ARGV[9]
            local userSlots = tonumber(redis.call('HGET', KEYS[1], userField) or '0')

            if slots + 1 > tonumber(ARGV[4])
                    or cpuUnits + tonumber(ARGV[1]) > tonumber(ARGV[5])
                    or memoryMb + tonumber(ARGV[2]) > tonumber(ARGV[6]) then
                return 0
            end

            if userSlots + 1 > tonumber(ARGV[10]) then
                redis.call('ZREM', KEYS[3], ARGV[8])
                return -1
            end

            -- FREE 可借用全部资源，但只要有 VIP 等待，就暂停放行新的 FREE。
            if tier == 'FREE' and redis.call('ZCARD', KEYS[4]) > 0 then
                return 0
            end

            redis.call('HINCRBY', KEYS[1], 'slots', 1)
            redis.call('HINCRBY', KEYS[1], 'cpuUnits', ARGV[1])
            redis.call('HINCRBY', KEYS[1], 'memoryMb', ARGV[2])
            redis.call('HINCRBY', KEYS[1], userField, 1)
            redis.call('HSET', KEYS[2], 'tier', tier, 'userField', userField,
                    'cpuUnits', ARGV[1], 'memoryMb', ARGV[2])
            redis.call('ZREM', KEYS[3], ARGV[8])
            return 1
            """, Long.class);

    /**
     * REDIS 模式原子释放资源：
     * 1. Reservation 不存在时直接返回，保证 release 幂等。
     * 2. 归还平台槽位、CPU、内存和单用户并发计数。
     * 3. 删除任务 Reservation，避免重复归还。
     */
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[2]) == 0 then
                return 0
            end

            local cpuUnits = tonumber(redis.call('HGET', KEYS[2], 'cpuUnits') or '0')
            local memoryMb = tonumber(redis.call('HGET', KEYS[2], 'memoryMb') or '0')
            local userField = redis.call('HGET', KEYS[2], 'userField')
            local slots = math.max(tonumber(redis.call('HGET', KEYS[1], 'slots') or '0') - 1, 0)
            local remainingCpuUnits = math.max(tonumber(redis.call('HGET', KEYS[1], 'cpuUnits') or '0') - cpuUnits, 0)
            local remainingMemoryMb = math.max(tonumber(redis.call('HGET', KEYS[1], 'memoryMb') or '0') - memoryMb, 0)

            redis.call('HSET', KEYS[1],
                    'slots', slots,
                    'cpuUnits', remainingCpuUnits,
                    'memoryMb', remainingMemoryMb)
            if userField then
                local userSlots = math.max(tonumber(redis.call('HGET', KEYS[1], userField) or '0') - 1, 0)
                if userSlots == 0 then
                    redis.call('HDEL', KEYS[1], userField)
                else
                    redis.call('HSET', KEYS[1], userField, userSlots)
                end
            end
            redis.call('DEL', KEYS[2])
            return 1
            """, Long.class);

    public ResourceSchedulerImpl(RunnerProperties properties, StringRedisTemplate redisTemplate) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void acquire(RunnerTaskMessage message) {
        // 1. taskId 是本地预留表和 Redis reservation 的幂等键，不能为空。
        if (message == null || message.getTaskId() == null) {
            throw new IllegalArgumentException("taskId 不能为空");
        }

        // 2. 实验类型决定资源需求；会员等级只决定权限、并发上限和排队待遇。
        long taskId = message.getTaskId();
        RunnerProperties.Tiers.Tier tier = tierConfig(message);
        RunnerProperties.PlatformResource platform = properties.getPlatform();
        ResourceTier resourceTier = ResourceTier.from(message.getTier());
        ExperimentType experimentType = ExperimentType.from(message.getMiddlewareType());
        if (experimentType == ExperimentType.UNKNOWN) {
            throw new IllegalArgumentException("不支持的实验类型：" + message.getMiddlewareType());
        }
        if (resourceTier == ResourceTier.FREE && experimentType != ExperimentType.REDIS) {
            throw new IllegalArgumentException("普通会员只允许运行 Redis 实验");
        }
        long cpuUnits = experimentType.requiredCpuUnits(properties.getWorkload());
        long memoryMb = experimentType.requiredMemoryMb(properties.getWorkload());
        String userKey = userKey(message, resourceTier);
        long maxCpuUnits = Math.round(
                (platform.getMaxCpus() - platform.getSystemReservedCpus()) * 100);
        long maxMemoryMb = platform.getMaxMemoryMb() - platform.getSystemReservedMemoryMb();
        if (maxCpuUnits <= 0 || maxMemoryMb <= 0) {
            throw new IllegalStateException("平台总资源必须大于系统固定保留资源");
        }
        if (cpuUnits > maxCpuUnits || memoryMb > maxMemoryMb) {
            throw new IllegalStateException("当前平台容量无法运行 " + experimentType
                    + " 实验，需要 cpus=" + cpuUnits / 100.0 + ", memoryMb=" + memoryMb);
        }
        long timeoutMs = resourceTier == ResourceTier.VIP
                ? platform.getVipAcquireTimeoutMs() : platform.getFreeAcquireTimeoutMs();
        long queuedAt = message.getQueuedAtEpochMs() == null
                ? System.currentTimeMillis() : message.getQueuedAtEpochMs();
        long deadline = queuedAt + timeoutMs;

        // 3. LOCAL 模式先进入本等级 FIFO；REDIS 模式由 Lua 的 ZSet 完成同样的排队。
        if (platform.getSchedulerMode() == RunnerProperties.SchedulerMode.LOCAL) {
            enqueueLocal(taskId, resourceTier);
        }

        try {
            // 4. 队首任务立即尝试一次；资源不足时轮询，但后来的任务不能插队。
            do {
                // 消息在 RabbitMQ 中等待的时间也计入期限，过期任务不能再占用新释放的资源。
                if (System.currentTimeMillis() >= deadline) {
                    break;
                }
                int acquireResult;
                if (platform.getSchedulerMode() == RunnerProperties.SchedulerMode.LOCAL) {
                    acquireResult = tryReserveLocal(taskId, resourceTier, userKey,
                            tier.getMaxConcurrentPerUser(), cpuUnits, memoryMb,
                            maxCpuUnits, maxMemoryMb);
                } else {
                    acquireResult = tryReserveRedis(taskId, resourceTier, userKey,
                            tier.getMaxConcurrentPerUser(), cpuUnits, memoryMb,
                            maxCpuUnits, maxMemoryMb, deadline);
                }

                if (acquireResult == -1) {
                    throw new ResourceBusyException("单用户并发任务数已达上限，taskId=" + taskId);
                }
                if (acquireResult == 1) {
                    log.info("资源获取成功，taskId={}, mode={}, tier={}, experimentType={}, cpus={}, memoryMb={}",
                            taskId, platform.getSchedulerMode(), resourceTier, experimentType,
                            cpuUnits / 100.0, memoryMb);
                    return;
                }

                long remainingMs = deadline - System.currentTimeMillis();
                if (remainingMs <= 0) {
                    break;
                }

                // sleep 只降低轮询频率；真正的先后顺序由 FIFO / ZSet 队首保证。
                try {
                    Thread.sleep(Math.min(POLL_MS, remainingMs));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ResourceBusyException("等待资源时任务被中断，taskId=" + taskId, e);
                }
            } while (true);
        } finally {
            // 成功时已从等待队列移除；超时或中断时在这里清掉残留。
            removeWaitingTask(taskId, resourceTier, platform.getSchedulerMode());
        }

        throw new ResourceBusyException("等待平台资源超时，taskId=" + taskId);
    }

    @Override
    public void release(RunnerTaskMessage message) {
        // 1. 没有 taskId 无法定位 Reservation，按幂等语义直接返回。
        if (message == null || message.getTaskId() == null) {
            return;
        }

        // 2. 多实例模式执行 Lua，在 Redis 内原子归还全部计数。
        if (properties.getPlatform().getSchedulerMode() == RunnerProperties.SchedulerMode.REDIS) {
            redisTemplate.execute(RELEASE_SCRIPT,
                    List.of(RunnerRedisKeys.RESOURCE_USAGE,
                            RunnerRedisKeys.resourceReservationKey(message.getTaskId())));
            return;
        }

        // 3. 单实例模式在同一个 JVM 锁中删除 Reservation 并归还全部计数。
        synchronized (localBudgetLock) {
            Reservation reservation = held.remove(message.getTaskId());
            if (reservation == null) {
                return;
            }
            reservedSlots--;
            reservedCpuUnits -= reservation.cpuUnits();
            reservedMemoryMb -= reservation.memoryMb();
            int userSlots = runningTasksByUser.getOrDefault(reservation.userKey(), 0) - 1;
            if (userSlots <= 0) {
                runningTasksByUser.remove(reservation.userKey());
            } else {
                runningTasksByUser.put(reservation.userKey(), userSlots);
            }
        }
    }

    @Override
    public String usageText() {
        long cpuUnits;
        long memoryMb;
        if (properties.getPlatform().getSchedulerMode() == RunnerProperties.SchedulerMode.LOCAL) {
            synchronized (localBudgetLock) {
                cpuUnits = reservedCpuUnits;
                memoryMb = reservedMemoryMb;
            }
        } else {
            Object cpu = redisTemplate.opsForHash().get(RunnerRedisKeys.RESOURCE_USAGE, "cpuUnits");
            Object memory = redisTemplate.opsForHash().get(RunnerRedisKeys.RESOURCE_USAGE, "memoryMb");
            cpuUnits = cpu == null ? 0 : Long.parseLong(cpu.toString());
            memoryMb = memory == null ? 0 : Long.parseLong(memory.toString());
        }
        RunnerProperties.PlatformResource platform = properties.getPlatform();
        long schedulableCpuUnits = Math.round(
                (platform.getMaxCpus() - platform.getSystemReservedCpus()) * 100);
        long schedulableMemoryMb = platform.getMaxMemoryMb() - platform.getSystemReservedMemoryMb();
        long cpuPercent = Math.round(cpuUnits * 100.0 / schedulableCpuUnits);
        long memoryPercent = Math.round(memoryMb * 100.0 / schedulableMemoryMb);
        return "cpu=" + cpuPercent + "%, mem=" + memoryPercent + "%";
    }

    private RunnerProperties.Tiers.Tier tierConfig(RunnerTaskMessage message) {
        return switch (ResourceTier.from(message.getTier())) {
            case FREE -> properties.getTiers().getFree();
            case VIP -> properties.getTiers().getVip();
        };
    }

    private int tryReserveLocal(long taskId, ResourceTier resourceTier, String userKey,
            int maxConcurrentPerUser, long cpuUnits, long memoryMb,
            long maxCpuUnits, long maxMemoryMb) {
        // taskId 登记、槽位、CPU 和内存必须在同一个临界区内检查和更新。
        synchronized (localBudgetLock) {
            // 同一任务重复 acquire 直接成功，避免重复占用资源。
            if (held.containsKey(taskId)) {
                return 1;
            }
            Deque<Long> waitingTasks = resourceTier == ResourceTier.VIP
                    ? vipWaitingTasks : freeWaitingTasks;
            if (!Long.valueOf(taskId).equals(waitingTasks.peekFirst())) {
                return 0;
            }
            if (runningTasksByUser.getOrDefault(userKey, 0) >= maxConcurrentPerUser) {
                // 超限任务直接离开队首，不能挡住同等级的其他用户。
                waitingTasks.removeFirst();
                return -1;
            }
            // 不固定预留资源：VIP 等待时暂停新 FREE，下一份空闲资源自然优先给 VIP。
            if (resourceTier == ResourceTier.FREE && !vipWaitingTasks.isEmpty()) {
                return 0;
            }
            if (reservedSlots + 1 > properties.getPlatform().getMaxConcurrent()
                    || reservedCpuUnits + cpuUnits > maxCpuUnits
                    || reservedMemoryMb + memoryMb > maxMemoryMb) {
                return 0;
            }

            // 三项资源都满足后再一次性登记，保证不会部分占用。
            reservedSlots++;
            reservedCpuUnits += cpuUnits;
            reservedMemoryMb += memoryMb;
            runningTasksByUser.merge(userKey, 1, Integer::sum);
            held.put(taskId, new Reservation(resourceTier, userKey, cpuUnits, memoryMb));
            waitingTasks.removeFirst();
            return 1;
        }
    }

    private int tryReserveRedis(long taskId, ResourceTier resourceTier, String userKey,
            int maxConcurrentPerUser, long cpuUnits, long memoryMb,
            long maxCpuUnits, long maxMemoryMb, long deadline) {
        // Lua 在 Redis 内一次完成检查、计数和 taskId 登记，避免多实例并发竞态。
        Long acquired = redisTemplate.execute(
                ACQUIRE_SCRIPT,
                List.of(RunnerRedisKeys.RESOURCE_USAGE,
                        RunnerRedisKeys.resourceReservationKey(taskId),
                        waitingKey(resourceTier),
                        RunnerRedisKeys.RESOURCE_WAITING_VIP),
                Long.toString(cpuUnits),
                Long.toString(memoryMb),
                resourceTier.name(),
                Integer.toString(properties.getPlatform().getMaxConcurrent()),
                Long.toString(maxCpuUnits),
                Long.toString(maxMemoryMb),
                Long.toString(deadline),
                String.format("%020d", taskId),
                userKey,
                Integer.toString(maxConcurrentPerUser),
                Long.toString(System.currentTimeMillis()));
        return acquired == null ? 0 : acquired.intValue();
    }

    private void enqueueLocal(long taskId, ResourceTier resourceTier) {
        synchronized (localBudgetLock) {
            if (held.containsKey(taskId)) {
                return;
            }
            Deque<Long> waitingTasks = resourceTier == ResourceTier.VIP
                    ? vipWaitingTasks : freeWaitingTasks;
            if (!waitingTasks.contains(taskId)) {
                waitingTasks.addLast(taskId);
            }
        }
    }

    private void removeWaitingTask(long taskId, ResourceTier resourceTier,
            RunnerProperties.SchedulerMode schedulerMode) {
        if (schedulerMode == RunnerProperties.SchedulerMode.REDIS) {
            redisTemplate.opsForZSet().remove(waitingKey(resourceTier), String.format("%020d", taskId));
            return;
        }
        synchronized (localBudgetLock) {
            Deque<Long> waitingTasks = resourceTier == ResourceTier.VIP
                    ? vipWaitingTasks : freeWaitingTasks;
            waitingTasks.remove(taskId);
        }
    }

    private String waitingKey(ResourceTier resourceTier) {
        return resourceTier == ResourceTier.VIP
                ? RunnerRedisKeys.RESOURCE_WAITING_VIP
                : RunnerRedisKeys.RESOURCE_WAITING_FREE;
    }

    private String userKey(RunnerTaskMessage message, ResourceTier resourceTier) {
        // 兼容升级前已在队列中的旧消息：没有 userId 时按任务隔离，不误伤其他用户。
        String userId = message.getUserId() == null
                ? "legacy-task-" + message.getTaskId()
                : message.getUserId().toString();
        return "userSlots:" + userId;
    }
}
