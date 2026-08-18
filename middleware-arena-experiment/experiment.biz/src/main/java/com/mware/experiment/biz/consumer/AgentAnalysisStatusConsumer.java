package com.mware.experiment.biz.consumer;

/**
 * Python Agent -> Experiment 的分析状态/结果消费者占位。
 *
 * <p>1. 监听 agent.analysis.status.queue，按 analysisId 幂等更新 experiment_analysis。</p>
 * <p>2. ANALYZING 更新 currentStage/progress；SUCCESS 落结构化结果；FAILED 落错误信息。</p>
 * <p>3. 只有 analysis 最终落库成功后才 ACK，失败按消费重试策略处理。</p>
 *
 * TODO:
 * 1. 增加 @RabbitListener(queues = AgentRabbitConfig.QUEUE_STATUS)。
 * 2. 反序列化 AgentAnalysisStatusMessage，并校验 analysisId/taskId 对应关系。
 * 3. SUCCESS 解析 resultJson -> bottleneck/confidence/evidence/hypotheses/suggestions/report。
 * 4. 补 manual ack / retry / DLQ 策略；状态消息是否单独建 DLQ在实现阶段定稿。
 */
public class AgentAnalysisStatusConsumer {
    // TODO[Agent MQ]：按上面的 1/2/3/4 实现。
}
