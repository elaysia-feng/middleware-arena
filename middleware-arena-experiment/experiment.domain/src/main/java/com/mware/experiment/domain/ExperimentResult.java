package com.mware.experiment.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 实验结果实体：压测指标的结构化落库（排行榜可直接 ORDER BY qps DESC）。
 * <p>
 * 由 experiment-service 持久化；runner 只采集回传原始指标（metricsJson），
 * 结构化字段由实验服务解析后填充。一任务一结果（task_id 唯一）。
 */
@Data
@TableName("experiment_result")
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExperimentResult {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 实验任务 ID（experiment_task.id），唯一 */
    private Long taskId;

    /** 吞吐量 QPS */
    private Double qps;

    /** P95 延迟（毫秒） */
    private Long p95Ms;

    /** 错误率（0~1） */
    private Double errorRate;

    /** 平均 CPU 使用率（0~1） */
    private Double avgCpu;

    /** 峰值内存（MB） */
    private Long peakMemoryMb;

    /** 原始完整指标（JSON） */
    private String metricsJson;

    private LocalDateTime createdAt;
}
