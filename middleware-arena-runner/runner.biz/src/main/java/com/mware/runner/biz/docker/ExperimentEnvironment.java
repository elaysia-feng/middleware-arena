package com.mware.runner.biz.docker;

import com.mware.runner.biz.config.ExperimentType;
import com.mware.runner.dto.RunnerTaskMessage;

/**
 * 单个实验的容器环境编排：建独立网络 → 按实验类型起中间件 + SUT → 供 k6 压测 → 清理。
 * <p>
 * 每个实验一个独立 docker network（task-{taskId}-net），容器名互不冲突，
 * k6 通过容器名直连 SUT（如 http://ma-task-1001-product:8080），
 * 不需要宿主端口池 / 端口抢占（对齐方案第 6 点）。
 * <p>
 * 多 SUT 实验（SEATA = order + storage + account）已支持：按 {@code ExperimentType.sutSpecs()}
 * 逐个启动候选镜像容器，k6 只压主链路入口（{@code sutRole()}）。
 * <p>
 * 数据一致性说明：baseline 与 candidate 是两个独立任务、各自新建整套容器，
 * 实验数据天然隔离，无需显式 FLUSHDB / 清队列 / 重建索引；
 * 仅当 {@code shared-services.enabled=true} 复用宿主机中间件时数据才跨任务共享，
 * 该场景由业务侧（experiment-service）负责清理，Runner 不动宿主机共享数据。
 */
public interface ExperimentEnvironment {

    /**
     * 启动实验环境：创建独立网络 + 按实验类型启动中间件与 SUT 容器。
     *
     * @param sutImage candidate 构建产物 / baseline 预构建镜像名
     * @return SUT 的 k6 压测基址（容器名直连）
     */
    String start(RunnerTaskMessage message, ExperimentType type, String sutImage);

    /** 清理：删除全部实验容器（SUT + 中间件）+ 移除实验网络（容忍容器/网络已不存在）。 */
    void teardown(RunnerTaskMessage message, ExperimentType type);
}
