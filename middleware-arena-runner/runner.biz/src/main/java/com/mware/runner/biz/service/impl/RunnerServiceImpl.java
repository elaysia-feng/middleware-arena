package com.mware.runner.biz.service.impl;

import com.mware.runner.biz.config.InstanceInfo;
import com.mware.runner.biz.config.RunnerRedisKeys;
import com.mware.runner.biz.config.RunningTaskManager;
import com.mware.runner.biz.config.RunnerTaskExecutorConfig;
import com.mware.runner.biz.service.RunnerService;
import com.mware.runner.dto.RunnerTaskMessage;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.concurrent.Future;

/**
 * Runner 业务实现（无持久化实体，不注入任何 Mapper）。
 * <p>
 * TODO[Runner]：接入 RabbitMQ + docker + k6 后，按各方法步骤逐个实现；
 * 每阶段结束后回传进度（MQ / Feign 通知 experiment 更新 ExperimentTask）。
 */
@Service
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

    @Override
    public RunnerTaskMessage build(RunnerTaskMessage message) {
        // TODO[Runner]：构建中间件镜像 / 二进制
        // 1. 解析 filesJson / runParamsJson 决定中间件类型与版本（docker build / 拉取固定版本镜像）
        // 2. 记录构建产物，回传阶段进度 BUILDING
        return message;
    }

    @Override
    public RunnerTaskMessage run(RunnerTaskMessage message) {
        // TODO[Runner]：启动 Docker 容器
        // 1. 用构建产物 + runParamsJson 启动容器（Docker SDK / docker CLI）
        // 2. 端口冲突 / 资源不足须捕获并回传 FAILED + errorMessage
        return message;
    }

    @Override
    public RunnerTaskMessage benchmark(RunnerTaskMessage message) {
        // TODO[Runner]：执行 k6 压测
        // 1. 由 runParamsJson 生成 k6 脚本（并发阶梯、阶段时长、超时来自 runParamsJson）
        // 2. 以子进程 / 容器执行 k6，回传阶段进度 BENCHMARKING
        return message;
    }

    @Override
    public RunnerTaskMessage collectMetrics(RunnerTaskMessage message) {
        // TODO[Runner]：采集指标
        // 1. 解析 k6 输出（吞吐 / 错误率 / P95 延迟 / 并发）+ 系统指标（CPU / 内存）
        // 2. 回传 experiment 持久化到 ExperimentResult（结构化字段 + metricsJson）
        return message;
    }

    @Override
    public void cleanup(RunnerTaskMessage message) {
        // TODO[Runner]：清理资源
        // 1. 停止容器、删除临时镜像、释放端口
        // 2. 删除临时 k6 脚本文件
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
                build(message);
                run(message);
                benchmark(message);
                collectMetrics(message);
                // 流水线收尾：停止容器 / 删临时镜像 / 释放端口（取消中断时 finally 仍执行）
                cleanup(message);
            } finally {
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
}
