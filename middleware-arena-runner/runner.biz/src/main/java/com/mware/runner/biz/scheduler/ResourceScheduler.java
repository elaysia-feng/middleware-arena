package com.mware.runner.biz.scheduler;

import com.mware.runner.biz.config.RunnerProperties;
import com.mware.runner.dto.RunnerTaskMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * 平台级资源调度器——"有资源才启动"的守门人。
 * <p>
 * Runner 收到任务后先 {@link #acquire} 抢资源，成功才进入 build/run 流水线；
 * 资源不足时 bounded 等待 {@code platform.acquire-timeout-ms}，仍不足抛
 * {@link ResourceBusyException} 交给 execute 重新排队，绝不创建容器。
 * <p>
 * 占用口径 = CPU（核）+ 内存（MB）+ 并发槽位（全局 max-concurrent），三者同时满足才放行。
 * tier 决定单实验额度；平台全局上限兜底，防止大量 FREE 用户把机器吃爆。
 *
 * TODO[Runner]：
 * <ul>
 *   <li>占用量改以真实容器 docker stats 为准（先按 tier 上限预留、跑完按实测归还）；</li>
 *   <li>多实例部署时本调度器需换分布式实现（Redis 原子计数），否则实例间互不知道对方占用。</li>
 * </ul>
 */
@Component
@Slf4j
public class ResourceScheduler {

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

    public ResourceScheduler(RunnerProperties properties) {
        this.properties = properties;
        this.globalSlots = new Semaphore(properties.getPlatform().getMaxConcurrent());
    }

    /**
     * 抢资源（阻塞轮询直到拿到或超时）。
     *
     * @throws ResourceBusyException 超时仍无资源 → execute 应重新入队而不是建容器
     */
    public void acquire(RunnerTaskMessage message) {
        Long taskId = message.getTaskId();
        RunnerProperties.PlatformResource platform = properties.getPlatform();
        RunnerProperties.Tiers.Tier tier = tierConfig(message);

        double cpus = tier.getCpus();
        long memoryMb = tier.getMemoryMb();
        long cpuUnits = Math.round(cpus * 100);
        long maxCpuUnits = Math.round(platform.getMaxCpus() * 100);
        long maxMemoryMb = platform.getMaxMemoryMb();
        long deadline = System.currentTimeMillis() + platform.getAcquireTimeoutMs();

        while (!tryReserve(cpuUnits, memoryMb, maxCpuUnits, maxMemoryMb)) {
            if (System.currentTimeMillis() >= deadline) {
                throw new ResourceBusyException("平台资源不足：需 cpus=" + cpus + ", mem=" + memoryMb
                        + "MB，taskId=" + taskId + " 等待 " + platform.getAcquireTimeoutMs() + "ms 后仍无空位");
            }
            try {
                Thread.sleep(POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ResourceBusyException("资源等待被中断：taskId=" + taskId, e);
            }
        }

        held.put(taskId, new Reservation(cpus, memoryMb));
        log.info("资源已分配 taskId={}, cpus={}, mem={}MB, 占用={}", taskId, cpus, memoryMb, usageText());
    }

    /**
     * 归还资源（幂等：未 acquire / 已释放的任务直接返回，execute 的 finally 里安全调用）。
     */
    public void release(RunnerTaskMessage message) {
        Long taskId = message.getTaskId();
        Reservation r = held.remove(taskId);
        if (r == null) {
            return;
        }
        synchronized (budgetLock) {
            reservedCpuUnits -= Math.round(r.cpus() * 100);
            reservedMemoryMb -= r.memoryMb();
        }
        globalSlots.release();
        log.info("资源已释放 taskId={}, 占用={}", taskId, usageText());
    }

    /** 当前资源占用文本（日志 / 健康检查） */
    public String usageText() {
        RunnerProperties.PlatformResource p = properties.getPlatform();
        long cpu, mem;
        synchronized (budgetLock) {
            cpu = reservedCpuUnits;
            mem = reservedMemoryMb;
        }
        long cpuPct = Math.round(cpu * 100.0 / Math.max(1, Math.round(p.getMaxCpus() * 100)));
        long memPct = Math.round(mem * 100.0 / Math.max(1, p.getMaxMemoryMb()));
        return String.format("cpu=%d%%, mem=%d%%", cpuPct, memPct);
    }

    private RunnerProperties.Tiers.Tier tierConfig(RunnerTaskMessage message) {
        return switch (ResourceTier.from(message.getTier())) {
            case VIP -> properties.getTiers().getVip();
            case FREE -> properties.getTiers().getFree();
        };
    }

    /** 槽位 + CPU + 内存三者同时满足才真正占用；任一不满足则归还槽位并返回 false */
    private boolean tryReserve(long cpuUnits, long memoryMb, long maxCpuUnits, long maxMemoryMb) {
        if (!globalSlots.tryAcquire()) {
            return false;
        }
        synchronized (budgetLock) {
            if (reservedCpuUnits + cpuUnits <= maxCpuUnits
                    && reservedMemoryMb + memoryMb <= maxMemoryMb) {
                reservedCpuUnits += cpuUnits;
                reservedMemoryMb += memoryMb;
                return true;
            }
        }
        globalSlots.release();
        return false;
    }
}
