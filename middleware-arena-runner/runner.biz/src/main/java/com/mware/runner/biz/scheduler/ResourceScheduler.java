package com.mware.runner.biz.scheduler;

import com.mware.runner.biz.config.ResourceBusyException;
import com.mware.runner.dto.RunnerTaskMessage;

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
public interface ResourceScheduler {

    /**
     * 抢资源（阻塞轮询直到拿到或超时）。
     *
     * @throws ResourceBusyException 超时仍无资源 → execute 应重新入队而不是建容器
     */
    void acquire(RunnerTaskMessage message) throws ResourceBusyException;

    /**
     * 归还资源（幂等：未 acquire / 已释放的任务直接返回，execute 的 finally 里安全调用）。
     */
    void release(RunnerTaskMessage message);

    /** 当前资源占用文本（日志 / 健康检查） */
    String usageText();
}
