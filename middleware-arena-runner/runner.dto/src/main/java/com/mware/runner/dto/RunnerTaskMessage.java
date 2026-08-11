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

    /**
     * 中间件类型（redis / rabbitmq / elasticsearch / seata），决定实验容器拓扑，
     * 见 {@code ExperimentType}。可选：experiment 侧模板已有 {@code middlewareType}，
     * 投递时应带上；未带时回退 {@code ExperimentType.UNKNOWN}（TODO：从 runParamsJson 兜底解析）。
     */
    private String middlewareType;

    /** 资源等级（FREE / VIP），决定单实验 CPU/内存额度，缺省按 FREE（见 ResourceScheduler） */
    private String tier;

    /**
     * 是否 baseline 预构建镜像任务：true=用预构建 {@code ma-{type}-baseline:v1}，跳过现场编译；
     * false/缺省=现场 build candidate SUT。baseline / candidate 串行压测（不并行），由 experiment 侧编排。
     */
    private Boolean baseline;
}
