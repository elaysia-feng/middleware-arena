package com.mware.runner.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Runner 任务消息（RabbitMQ 消息契约，非数据库实体）。
 * <p>
 * runner-service 不持久化任务状态：任务状态由 experiment-service 的
 * {@code ExperimentTask} 持有，runner 只消费本消息执行 build → run → benchmark → collect
 * 流水线，并回传 progress / result 由 experiment-service 落库。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RunnerTaskMessage {

    /** 实验任务 ID（experiment_task.id） */
    private Long taskId;

    /** 实验版本 ID（experiment_version.id） */
    private Long versionId;

    /** 完整代码文件快照（JSON 数组，来自 experiment_version.filesJson） */
    private String filesJson;

    /** 压测 / 运行参数（JSON，来自 experiment_version.runParamsJson） */
    private String runParamsJson;

    /** task 类型：CREATE（创建执行） / CANCEL（取消）等 */
    private String taskType;
}
