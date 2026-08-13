package com.mware.runner.biz.execution.impl;

import com.mware.runner.biz.benchmark.K6Runner;
import com.mware.runner.biz.build.SutBuilder;
import com.mware.runner.biz.config.ExperimentType;
import com.mware.runner.biz.config.RunnerProperties;
import com.mware.runner.biz.config.RunnerRedisKeys;
import com.mware.runner.biz.config.RunnerTaskExecutorConfig;
import com.mware.runner.biz.docker.DockerService;
import com.mware.runner.biz.docker.ExperimentEnvironment;
import com.mware.runner.biz.execution.InstanceInfo;
import com.mware.runner.biz.execution.RunningTaskManager;
import com.mware.runner.biz.execution.RunnerService;
import com.mware.runner.biz.metrics.MetricsCollector;
import com.mware.runner.biz.progress.ProgressReporter;
import com.mware.runner.biz.scheduler.ResourceScheduler;
import com.mware.runner.dto.RunnerTaskMessage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

    /** 各阶段之间传递当前任务的实验类型、镜像和 SUT 地址。 */
    private final Map<Long, TaskContext> taskContexts = new ConcurrentHashMap<>();

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
        ExperimentType type = ExperimentType.from(message.getMiddlewareType());
        if (type == ExperimentType.UNKNOWN) {
            throw new IllegalArgumentException("不支持的实验类型: " + message.getMiddlewareType());
        }

        TaskContext context = new TaskContext(type);
        taskContexts.put(message.getTaskId(), context);
        context.status = "BUILDING";
        progressReporter.stage(message, "BUILDING");
        context.sutImage = sutBuilder.build(message, type);
        logStage(message, "BUILDING", "image=" + context.sutImage);
        return message;
    }

    @Override
    public RunnerTaskMessage run(RunnerTaskMessage message) {
        TaskContext context = taskContexts.get(message.getTaskId());
        context.status = "RUNNING";
        progressReporter.stage(message, "RUNNING");
        // start 过程中即使部分容器启动后失败，cleanup 也需要执行 teardown。
        context.environmentStarted = true;
        context.baseUrl = experimentEnvironment.start(message, context.type, context.sutImage);
        logStage(message, "RUNNING", "baseUrl=" + context.baseUrl);
        return message;
    }

    @Override
    public RunnerTaskMessage waitHealthy(RunnerTaskMessage message) {
        TaskContext context = taskContexts.get(message.getTaskId());
        context.status = "WAITING_HEALTH";
        progressReporter.stage(message, "WAITING_HEALTH");
        String healthUrl = context.baseUrl + "/actuator/health";
        boolean healthy = dockerService.waitHealthy(message.getTaskId(), healthUrl,
                properties.getDocker().getCommandTimeoutSeconds());
        if (!healthy) {
            throw new IllegalStateException("SUT 健康检查超时，taskId=" + message.getTaskId());
        }
        logStage(message, "WAITING_HEALTH", "healthUrl=" + healthUrl);
        return message;
    }

    @Override
    public RunnerTaskMessage benchmark(RunnerTaskMessage message) {
        TaskContext context = taskContexts.get(message.getTaskId());
        context.status = "BENCHMARKING";
        progressReporter.stage(message, "BENCHMARKING");
        k6Runner.run(message, context.type, context.baseUrl);
        logStage(message, "BENCHMARKING", "k6 completed");
        return message;
    }

    @Override
    public RunnerTaskMessage collectMetrics(RunnerTaskMessage message) {
        TaskContext context = taskContexts.get(message.getTaskId());
        context.status = "COLLECTING";
        progressReporter.stage(message, "COLLECTING");
        context.metricsJson = metricsCollector.collect(message, context.type);
        logStage(message, "COLLECTING", "metrics=" + context.metricsJson);
        return message;
    }

    @Override
    public void cleanup(RunnerTaskMessage message) {
        TaskContext context = taskContexts.get(message.getTaskId());
        if (context == null) {
            return;
        }

        context.status = "CLEANING";
        try {
            progressReporter.stage(message, "CLEANING");
        } catch (RuntimeException e) {
            // 状态通知失败不能阻止真正的 Docker 清理。
            log.warn("清理阶段状态回传失败 taskId={}", message.getTaskId(), e);
        }
        try {
            if (context.environmentStarted) {
                experimentEnvironment.teardown(message, context.type);
            }
        } finally {
            try {
                // baseline 是公共常驻镜像；只有 candidate 镜像需要按任务删除。
                if (!Boolean.TRUE.equals(message.getBaseline()) && context.sutImage != null) {
                    dockerService.removeImage(context.sutImage);
                }
            } finally {
                taskContexts.remove(message.getTaskId());
            }
        }
    }

    @Override
    public RunnerTaskMessage execute(RunnerTaskMessage message) {
        Long taskId = message.getTaskId();
        if (taskId == null) {
            throw new IllegalArgumentException("Runner 任务缺少 taskId");
        }
        if (ExperimentType.from(message.getMiddlewareType()) == ExperimentType.UNKNOWN) {
            throw new IllegalArgumentException("不支持的实验类型: " + message.getMiddlewareType());
        }

        // 1. 在消费线程中先获取资源。失败会直接抛给消费者，因此消息不会提前 ACK。
        resourceScheduler.acquire(message);

        // 2. 获取资源后再登记实例并提交流水线；登记或提交失败时立即归还资源。
        Future<?> future;
        try {
            stringRedisTemplate.opsForValue().set(
                    RunnerRedisKeys.instanceKey(taskId), instanceInfo.getInstanceId());
            future = runnerTaskExecutor.submit(() -> {
                String metricsJson = null;
                try {
                    try {
                        // 1. 构建镜像 → 2. 启动环境 → 3. 健康检查 → 4. 压测 → 5. 采集指标。
                        build(message);
                        run(message);
                        waitHealthy(message);
                        benchmark(message);
                        collectMetrics(message);
                        metricsJson = taskContexts.get(taskId).metricsJson;
                    } finally {
                        // 6. 无论成功、失败还是取消，都清理实验环境并归还资源。
                        try {
                            cleanup(message);
                        } finally {
                            resourceScheduler.release(message);
                            runningTaskManager.remove(String.valueOf(taskId));
                            stringRedisTemplate.delete(RunnerRedisKeys.instanceKey(taskId));
                        }
                    }
                    // cleanup 也成功后才回传 SUCCESS，避免任务成功但容器仍残留。
                    progressReporter.completed(message, metricsJson);
                } catch (RuntimeException e) {
                    progressReporter.failed(message, e);
                    log.error("Runner 任务执行失败 taskId={}", taskId, e);
                    throw e;
                }
            });
        } catch (RuntimeException e) {
            // 线程池拒绝任务时不会进入异步 finally，这里必须立即归还资源和实例登记。
            resourceScheduler.release(message);
            stringRedisTemplate.delete(RunnerRedisKeys.instanceKey(taskId));
            throw e;
        }

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
        TaskContext context = taskContexts.get(taskId);
        return context == null ? null : context.status;
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

    /** 只保存一次任务流水线各阶段必须共享的数据。 */
    private static class TaskContext {
        private final ExperimentType type;
        private String sutImage;
        private String baseUrl;
        private String metricsJson;
        private boolean environmentStarted;
        private volatile String status;

        private TaskContext(ExperimentType type) {
            this.type = type;
        }
    }
}
