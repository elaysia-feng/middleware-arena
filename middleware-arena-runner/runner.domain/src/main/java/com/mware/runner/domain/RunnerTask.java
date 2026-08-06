package com.mware.runner.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Runner 任务占位类（不持久化到数据库，仅内存流转）。
 * <p>
 * TODO：
 *   - 完整字段定义：middlewareType / version / config / k6Script 等
 *   - 状态机：pending → building → running → benchmarking → collecting → success / failed
 *   - 指标结构嵌入或关联 MetricsResult
 */
@Data
public class RunnerTask {

    /** 任务唯一标识（由上游 experiment-service 生成） */
    private String id;

    /** 上游实验任务 ID */
    private String taskId;

    /** 当前阶段状态 */
    private String status;
}
