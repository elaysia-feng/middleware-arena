package com.mware.experiment.mq.message;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Runner 任务消息（RabbitMQ 消息契约，非数据库实体）。
 * <p>
 * 与 runner 服务约定 JSON 字段：两端各自维护一份同名 DTO，避免跨服务模块依赖
 * （runner.dto 不引入 experiment）。runner-service 消费本消息执行
 * build → run → benchmark → collect 流水线，并回传 progress / result 由
 * experiment-service 落库。
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

    /** 任务类型， 比如说 createTask, CancelTask */
    private String taskType;
}
