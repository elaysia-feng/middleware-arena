package com.mware.runner.biz.execution;

import com.mware.runner.dto.RunnerTaskMessage;

/**
 * Runner 业务接口。
 * <p>
 * 0 个持久化实体：任务状态由 experiment-service 的 ExperimentTask 持有，
 * 压测结果由 experiment-service 的 ExperimentResult 持有；
 * runner 只消费 {@link RunnerTaskMessage} 执行 Docker / k6 流水线并回传 progress / result。
 */
public interface RunnerService {

    /** 根据 OSS 版本文件 / runParamsJson 构建中间件镜像 / 二进制 */
    RunnerTaskMessage build(RunnerTaskMessage message);

    /** 启动 Docker 容器 */
    RunnerTaskMessage run(RunnerTaskMessage message);

    /** 等待 SUT 健康检查通过（/actuator/health），健康前不进入压测 */
    RunnerTaskMessage waitHealthy(RunnerTaskMessage message);

    /** 执行 k6 压测（HTTP / gRPC / TCP） */
    RunnerTaskMessage benchmark(RunnerTaskMessage message);

    /** 采集指标（CPU / 内存 / 延迟 / QPS），回传 experiment 持久化到 ExperimentResult */
    RunnerTaskMessage collectMetrics(RunnerTaskMessage message);

    /** 清理：停止容器、删除临时镜像、释放端口 */
    void cleanup(RunnerTaskMessage message);

    /** 完整流水线编排：build → run → benchmark → collect → cleanup */
    RunnerTaskMessage execute(RunnerTaskMessage message);

    /** 查询任务进度：runner 无持久化，进度由 experiment-service 持有（回查或返回 null） */
    String getTaskStatus(Long taskId);

    /**
     * 取消本地登记的任务并清理 Redis 任务实例登记：
     * <ol>
     *   <li>{@code RunningTaskManager} 中断持有该任务 Future 的线程（幂等，未登记返回 false）；</li>
     *   <li>删除 {@code runner:task:instance:{taskId}} 登记键（幂等，取消后任务不再归属任何实例）。</li>
     * </ol>
     * 返回是否真正中断了 Future（未登记 / 取消失败返回 false，但登记键仍会被清理）。
     */
    boolean cancelTask(Long taskId);
}
