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

    /** 创建任务的用户 ID，用于限制单个用户的并发任务数。 */
    private Long userId;

    /** 实验版本 ID（experiment_version.id） */
    private Long versionId;

    /** 仅兼容旧版本数据；新任务不再通过 MQ 传输完整文件正文。 */
    private String filesJson;

    /** 版本文件在 OSS 中的对象 Key。 */
    private String filesObjectKey;

    /** OSS 对象压缩字节的 SHA-256。 */
    private String filesSha256;

    /** OSS 对象压缩后的字节数。 */
    private Long filesSize;

    /** 压测 / 运行参数（JSON，来自 experiment_version.runParamsJson） */
    private String runParamsJson;

    /** 任务类型， 比如说 createTask, CancelTask */
    private String taskType;

    /** 中间件类型，用于 runner 选择实验拓扑。 */
    private String middlewareType;

    /** 入队时会员等级快照：FREE / VIP。 */
    private String tier;

    /** 本次入队时间，用于计算端到端排队超时。 */
    private Long queuedAtEpochMs;

    /** 本次投递唯一标识。 */
    private String dispatchId;

    private Boolean baseline;
}
