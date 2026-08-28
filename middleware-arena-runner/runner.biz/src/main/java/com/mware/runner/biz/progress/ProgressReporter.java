package com.mware.runner.biz.progress;

import com.mware.runner.dto.RunnerTaskMessage;

/**
 * 阶段进度回传：build / run / waitHealthy / benchmark / collect / cleanup 各阶段开始时
 * 通知 experiment 更新 ExperimentTask 状态。
 * <p>
 * 通过 RabbitMQ 通知 experiment-service 更新任务状态。
 */
public interface ProgressReporter {

    /** 阶段名：BUILDING / RUNNING / WAITING_HEALTH / BENCHMARKING / COLLECTING / CLEANING */
    void stage(RunnerTaskMessage message, String stage);

    /** 回传阶段完成后的关键细节；日志通知失败不应中断任务本身。 */
    default void detail(RunnerTaskMessage message, String stage, String detail) {
    }

    /** 全部阶段完成，携带本次实验指标。 */
    void completed(RunnerTaskMessage message, String metricsJson);

    /** 任一阶段失败，携带错误信息。 */
    void failed(RunnerTaskMessage message, Throwable error);
}
