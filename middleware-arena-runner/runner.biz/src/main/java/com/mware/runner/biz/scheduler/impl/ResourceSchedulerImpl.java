package com.mware.runner.biz.scheduler.impl;

import com.mware.runner.biz.config.ResourceBusyException;
import com.mware.runner.biz.config.ResourceTier;
import com.mware.runner.biz.config.RunnerProperties;
import com.mware.runner.biz.scheduler.ResourceScheduler;
import com.mware.runner.dto.RunnerTaskMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * 平台级资源调度实现：槽位 + CPU + 内存三者同时满足才放行，bounded 等待超时抛 ResourceBusyException。
 */
@Component
@Slf4j
public class ResourceSchedulerImpl implements ResourceScheduler {

    /** 轮询等待间隔（ms） */
    private static final long POLL_MS = 500;

    private final RunnerProperties properties;

    /** 全局并发槽位 */
    private final Semaphore globalSlots;

    /** 已占用 CPU（0.01 核为单位，避免浮点累加漂移） */
    private long reservedCpuUnits;

    /** 已占用内存（MB） */
    private long reservedMemoryMb;

    /** 预算校验与增减互斥锁：实验低频（分钟级），synchronized 足够且无 CAS 竞态 */
    private final Object budgetLock = new Object();

    /** 已 acquire 的任务登记：taskId → 占用量，保证 release 幂等 */
    private final Map<Long, Reservation> held = new ConcurrentHashMap<>();

    private record Reservation(double cpus, long memoryMb) {
    }

    public ResourceSchedulerImpl(RunnerProperties properties) {
        this.properties = properties;
        this.globalSlots = new Semaphore(properties.getPlatform().getMaxConcurrent());
    }

    @Override
    public void acquire(RunnerTaskMessage message) {
        // TODO[Runner]：完整实现——按 tier 额度轮询抢占（POLL_MS 间隔、acquireTimeoutMs 超时），
        // 槽位 + CPU + 内存三者同时满足才放行，成功登记 held，超时抛 ResourceBusyException。
    }

    @Override
    public void release(RunnerTaskMessage message) {
        // TODO[Runner]：完整实现——幂等归还（held 未命中直接返回），释放槽位 + CPU + 内存预算。
    }

    @Override
    public String usageText() {
        // TODO[Runner]：完整实现——返回当前占用相对平台上限的文本（如 "cpu=50%, mem=30%"）。
        return "cpu=0%, mem=0%";
    }

    private RunnerProperties.Tiers.Tier tierConfig(RunnerTaskMessage message) {
        // TODO[Runner]：按 ResourceTier.from(message.getTier()) 返回 free / vip 对应额度配置。
        return properties.getTiers().getFree();
    }

    /** 槽位 + CPU + 内存三者同时满足才真正占用；任一不满足则归还槽位并返回 false */
    private boolean tryReserve(long cpuUnits, long memoryMb, long maxCpuUnits, long maxMemoryMb) {
        // TODO[Runner]：完整实现——槽位 + CPU + 内存三者同时满足才占用，任一不满足归还槽位返回 false。
        return false;
    }
}
