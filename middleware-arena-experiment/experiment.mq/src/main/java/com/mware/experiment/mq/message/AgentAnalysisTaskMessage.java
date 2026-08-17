package com.mware.experiment.mq.message;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Experiment -> Python Agent 的自动分析任务消息。
 *
 * <p>1. MQ 只传任务标识和路由元数据，不传完整代码、metrics、日志。</p>
 * <p>2. Python 端 AgentAnalysisTaskMessage 使用同名 camelCase JSON 字段。</p>
 * <p>3. analysisId + dispatchId 用于幂等，taskId/versionId 用于 Agent 再走 HTTP 拉取真实上下文。</p>
 *
 * TODO:
 * 1. experiment_analysis 表落地后，由 analysisId 对应一条分析任务记录。
 * 2. baselineTaskId 为空时由 Agent 根据实验策略补 baseline。
 * 3. analysisType 第一版只允许 PERFORMANCE_DIAGNOSIS，后续再扩展。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentAnalysisTaskMessage {

    private Long analysisId;
    private Long taskId;
    private Long userId;
    private Long versionId;
    private Long baselineTaskId;
    private String middlewareType;
    private String analysisType;
    private String triggerType;
    private String dispatchId;
    private Long queuedAtEpochMs;
}
