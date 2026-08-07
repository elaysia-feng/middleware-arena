package com.mware.runner.domain;

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
 * Runner 压测任务实体（持久化到 runner_task 表）。
 * <p>
 * 字段对齐 sql/init.sql 的 runner_task 表；
 * id 为本表自增主键，task_id 为上游 experiment-service 实验任务 ID（唯一）。
 */
@Data
@TableName("runner_task")
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RunnerTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 上游实验任务 ID（experiment_task.id），唯一 */
    private String taskId;

    /** 中间件类型：redis / rabbitmq / nginx / kafka / envoy 等 */
    private String middlewareType;

    /** 运行配置（JSON） */
    private String config;

    /** k6 压测脚本（内容或路径） */
    private String k6Script;

    /** 阶段状态：pending / building / running / benchmarking / collecting / success / failed / cancelled */
    private String status;

    /** 指标结果（JSON：CPU / 内存 / 延迟 / QPS） */
    private String metrics;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
