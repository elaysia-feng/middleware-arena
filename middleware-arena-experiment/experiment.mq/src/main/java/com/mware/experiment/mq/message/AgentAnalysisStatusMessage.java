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
 * <p>4. 消费与落库见 {@link com.mware.experiment.biz.consumer.AgentAnalysisStatusConsumer}：
 *    SUCCESS 落 bottleneck / confidence / evidence / hypotheses / suggestions / report；
 *    FAILED 落 errorCode / errorMessage；终态后同 analysisId 的消息幂等丢弃；
 *    是否允许用户手动 retry 由前端按 FAILED 状态自行发起（重新创建分析记录）。</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentAnalysisStatusMessage {

    private Long analysisId;
    private Long taskId;
    /** 投递批次，防止租约重派后的旧执行覆盖当前分析。 */
    private String dispatchId;
    private String status;
    private String currentStage;
    private Integer progress;
    private String resultJson;
    private String errorCode;
    private String errorMessage;
    private Long finishedAtEpochMs;
}
