package com.mware.experiment.mq.message;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Python Agent -> Experiment 的分析状态/结果消息。
 *
 * <p>1. ANALYZING / SUCCESS / FAILED 统一通过本消息回传。</p>
 * <p>2. resultJson 第一版可承载结构化诊断结果；报告过大时应改为对象存储引用。</p>
 * <p>3. Python 端 app/mq/messages.py 必须保持同名 camelCase JSON 字段。</p>
 *
 * TODO:
 * 1. experiment-service 增加消费者并按 analysisId 幂等更新 experiment_analysis。
 * 2. SUCCESS 时落 bottleneck/confidence/evidence/suggestions/report 等结构化字段。
 * 3. FAILED 时写 errorCode/errorMessage，并决定是否允许用户手动 retry。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentAnalysisStatusMessage {

    private Long analysisId;
    private Long taskId;
    private String status;
    private String currentStage;
    private Integer progress;
    private String resultJson;
    private String errorCode;
    private String errorMessage;
    private Long finishedAtEpochMs;
}
