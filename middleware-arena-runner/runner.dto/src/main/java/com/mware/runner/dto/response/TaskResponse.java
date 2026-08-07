package com.mware.runner.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Runner 压测任务响应对象（对外暴露）。
 * <p>
 * 字段对齐 {@code runner.domain.RunnerTask} 中对外展示所需字段；
 * k6Script / updatedAt 属内部细节，不暴露。
 */
@Data
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class TaskResponse {

    /** 本表自增主键（runner_task.id） */
    private Long id;

    /** 上游实验任务 ID */
    private String taskId;

    /** 中间件类型：redis / rabbitmq / nginx / kafka / envoy 等 */
    private String middlewareType;

    /** 运行配置（JSON） */
    private String config;

    /** 阶段状态：pending / building / running / benchmarking / collecting / success / failed / cancelled */
    private String status;

    /** 指标结果（JSON：CPU / 内存 / 延迟 / QPS），TODO[Runner]：接入采集后结构化填充 */
    private String metrics;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
