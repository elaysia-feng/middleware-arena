package com.mware.runner.biz.service.impl;

import com.mware.runner.biz.benchmark.K6Runner;
import com.mware.runner.biz.build.SutBuilder;
import com.mware.runner.biz.config.InstanceInfo;
import com.mware.runner.biz.config.RunnerProperties;
import com.mware.runner.biz.config.RunnerRedisKeys;
import com.mware.runner.biz.config.RunningTaskManager;
import com.mware.runner.biz.config.RunnerTaskExecutorConfig;
import com.mware.runner.biz.docker.DockerService;
import com.mware.runner.biz.docker.ExperimentEnvironment;
import com.mware.runner.biz.docker.ExperimentType;
import com.mware.runner.biz.metrics.MetricsCollector;
import com.mware.runner.biz.progress.ProgressReporter;
import com.mware.runner.biz.scheduler.ResourceBusyException;
import com.mware.runner.biz.scheduler.ResourceScheduler;
import com.mware.runner.biz.service.RunnerService;
import com.mware.runner.dto.RunnerTaskMessage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.concurrent.Future;

/**
 * Runner 业务实现（无持久化实体，不注入任何 Mapper）。
 * <p>
 * 核心方案（资源优先）：
 * <pre>
 *   execute(task) {
 *       resourceScheduler.acquire(task);   // 先抢资源，不足 → 重新排队，绝不建容器
 *       try {
 *           build → run → waitHealthy → benchmark → collectMetrics
 *       } finally {
 *           cleanup; resourceScheduler.release(task);
 *       }
 *   }
 * </pre>
 * 各阶段委托给对应框架类（SutBuilder / ExperimentEnvironment / K6Runner / MetricsCollector /
 * ProgressReporter），本类只做编排；阶段进度经 ProgressReporter 回传 experiment 更新 ExperimentTask。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RunnerServiceImpl implements RunnerService {

    /** 本地运行任务登记表（taskId → Future），取消任务时使用 */
    private final RunningTaskManager runningTaskManager;

    /** 任务消费侧自定义线程池（core=4 / max=16 / queue=100，满则 CallerRunsPolicy 反压） */
    @Qualifier(RunnerTaskExecutorConfig.BEAN_NAME)
    private final ThreadPoolTaskExecutor runnerTaskExecutor;

    /** 本实例唯一标识，CREATE 时登记到 Redis（runner:task:instance:{taskId} → instanceId） */
    private final InstanceInfo instanceInfo;

    /** Redis 模板：任务实例登记 / 任务结束后反登记 */
    private final StringRedisTemplate stringRedisTemplate;

    /** 平台资源调度器：先抢资源再进流水线（"有资源才启动"） */
    private final ResourceScheduler resourceScheduler;

    /** 用户代码 → candidate SUT 镜像（baseline 用预构建镜像） */
    private final SutBuilder sutBuilder;

    /** 实验容器环境编排：建网络 + 按类型起中间件/SUT + 清理 */
    private final ExperimentEnvironment experimentEnvironment;

    /** docker CLI 封装（镜像名 / 命名契约复用） */
    private final DockerService dockerService;

    /** k6 压测 */
    private final K6Runner k6Runner;

    /** 指标采集 */
    private final MetricsCollector metricsCollector;

    /** 阶段进度回传 */
    private final ProgressReporter progressReporter;

    /** Runner 配置（镜像名 / 资源额度等） */
    private final RunnerProperties properties;

    @Override
    public RunnerTaskMessage build(RunnerTaskMessage message) {
        progressReporter.stage(message, "BUILDING");
        ExperimentType type = ExperimentType.from(message.getMiddlewareType());
        // 构建 candidate SUT 镜像（baseline 用预构建镜像）；镜像名 ma-task-{taskId}-sut
        // 确定性生成，run() 按同一规则取用，无需在消息间传递构建产物。
        sutBuilder.build(message, type);
        return message;
    }

    @Override
    public RunnerTaskMessage run(RunnerTaskMessage message) {
        progressReporter.stage(message, "RUNNING");
        ExperimentType type = ExperimentType.from(message.getMiddlewareType());
        String sutImage = Boolean.TRUE.equals(message.getBaseline())
                ? type.baselineImage(properties.getImages())
                : dockerService.sutImageName(message.getTaskId());
        String baseUrl = experimentEnvironment.start(message, type, sutImage);
        // TODO[Runner]：baseUrl 需要跨阶段传给 benchmark/collectMetrics，
        // 引入 TaskContext（ConcurrentHashMap<taskId, ctx>）或在消息上挂载运行上下文
        logStage(message, "RUNNING", "sutUrl=" + baseUrl);
        return message;
    }

    @Override
    public RunnerTaskMessage waitHealthy(RunnerTaskMessage message) {
        progressReporter.stage(message, "WAITING_HEALTH");
        ExperimentType type = ExperimentType.from(message.getMiddlewareType());
        // TODO[Runner]：DockerService.waitHealthy 轮询 /actuator/health（超时时间来自 runParamsJson）；
        // 不健康则回传 FAILED + errorMessage，不进入压测
        return message;
    }

    @Override
    public RunnerTaskMessage benchmark(RunnerTaskMessage message) {
        progressReporter.stage(message, "BENCHMARKING");
        ExperimentType type = ExperimentType.from(message.getMiddlewareType());
        // TODO[Runner]：k6 临时容器压测（smoke → warmup → 正式），见 K6Runner
        return message;
    }

    @Override
    public RunnerTaskMessage collectMetrics(RunnerTaskMessage message) {
        progressReporter.stage(message, "COLLECTING");
        ExperimentType type = ExperimentType.from(message.getMiddlewareType());
        // TODO[Runner]：解析 k6 summary.json + docker stats，回传 experiment 持久化，见 MetricsCollector
        return message;
    }

    @Override
    public void cleanup(RunnerTaskMessage message) {
        progressReporter.stage(message, "CLEANING");
        ExperimentType type = ExperimentType.from(message.getMiddlewareType());
        // 清理临时容器 + 实验网络（幂等容忍不存在）；k6 --rm 容器跑完自删
        experimentEnvironment.teardown(message, type);
    }

    @Override
    public RunnerTaskMessage execute(RunnerTaskMessage message) {
        Long taskId = message.getTaskId();

        // 核心方案（T4）：CREATE 时由执行实例主动登记"该任务属于本实例"，
        // 供 T5 per-instance 定向取消 / 任务归属判断读取（键名契约见 RunnerRedisKeys）。
        stringRedisTemplate.opsForValue().set(
                RunnerRedisKeys.instanceKey(taskId), instanceInfo.getInstanceId());

        // 提交到自定义线程池执行流水线；finally 反登记，任务结束后
        // RunningTaskManager 与 Redis 均不残留。
        Future<?> future = runnerTaskExecutor.submit(() -> {
            try {
                // 资源不足（ResourceBusyException）→ 重新排队，不创建容器
                resourceScheduler.acquire(message);
                try {
                    build(message);
                    run(message);
                    waitHealthy(message);
                    benchmark(message);
                    collectMetrics(message);
                } finally {
                    // 流水线收尾：停止容器 / 删临时镜像 / 释放端口（取消中断时 finally 仍执行）
                    cleanup(message);
                }
            } catch (ResourceBusyException e) {
                // TODO[Runner]：平台资源不足 → 把任务重新投回 runner.task.queue（延迟重投），
                // 保留消息在队列等资源，而不是像普通异常那样重试 3 次后进 DLQ；
                // 需在 TaskConsumer 识别本异常走 requeue-with-delay 分支
                log.warn("平台资源不足，任务待重投：{}", e.getMessage());
            } finally {
                resourceScheduler.release(message);   // 幂等：未 acquire 时为 no-op
                runningTaskManager.remove(String.valueOf(taskId));
                stringRedisTemplate.delete(RunnerRedisKeys.instanceKey(taskId));
            }
        });

        // submit 返回后立刻登记 Future（先 submit 得 future 再 register，
        // 避免 register 在 submit 前拿不到 Future 的竞态窗口）。
        runningTaskManager.register(String.valueOf(taskId), future);

        // 兜底：占位流水线瞬间完成时（任务先于 register 结束），finally 的 remove
        // 发生在 register 之前，会留下已完成的 Future 残留；补一次清理保证登记表
        // 不存过期条目（真实 k6 长任务不触发，仅占位阶段可能）。
        if (future.isDone()) {
            runningTaskManager.remove(String.valueOf(taskId));
        }

        return message;
    }

    @Override
    public String getTaskStatus(Long taskId) {
        // TODO[Runner]：runner 无持久化，进度在 experiment-service
        // 走 Feign 回查 experiment，或返回 null 由调用方决定
        return null;
    }

    @Override
    public boolean cancelTask(Long taskId) {
        // 1. 取消本地运行的任务（RunningTaskManager key 统一转 String，幂等）
        boolean cancelled = runningTaskManager.cancel(String.valueOf(taskId));

        // 2. 幂等清理 Redis 任务实例登记：取消后该任务不再归属任何实例，防残留。
        //    即使 Future 未命中（任务已结束 / 未登记）也一并删除登记键，
        //    保证 experiment 侧再次取消时查不到实例而跳过，符合"任务已不可取消"语义。
        //    注：execute 的 finally 也会删同键，此处先删可提前释放归属；
        //    任务线程中断后 finally 里的 delete 幂等（Redis DEL 不存在的键返回 0，无副作用）。
        stringRedisTemplate.delete(RunnerRedisKeys.instanceKey(taskId));

        return cancelled;
    }

    private void logStage(RunnerTaskMessage message, String stage, String detail) {
        log.info("阶段完成 taskId={}, stage={}, {}", message.getTaskId(), stage, detail);
    }
}
