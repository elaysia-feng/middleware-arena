package com.mware.experiment.mq.producer;

import org.springframework.stereotype.Component;

/**
 * Experiment -> Python Agent 自动分析任务生产者占位。
 *
 * <p>1. Runner SUCCESS 且 experiment_result 已落库后，再构造 AgentAnalysisTaskMessage。</p>
 * <p>2. 投递到 agent.analysis.exchange，routingKey=agent.analysis。</p>
 * <p>3. 可靠性策略复制 RunnerCreaetTaskProducer：Publisher Confirm + Mandatory Return，确认成功后才将 analysis 状态置 QUEUED。</p>
 *
 * TODO:
 * 1. 注入 ConnectionFactory + Jackson2JsonMessageConverter，自建 RabbitTemplate。
 * 2. send(AgentAnalysisTaskMessage) 使用 analysisId 作为 CorrelationData.id。
 * 3. 等待 Broker Confirm；NACK / return / timeout 时抛异常，交补偿逻辑回捞。
 * 4. 与 experiment_analysis 的 CREATED -> QUEUED 状态机接通。
 */
@Component
public class AgentAnalysisTaskProducer {
    // TODO[Agent MQ]：按上面的 1/2/3/4 实现，不在骨架阶段提前写业务逻辑。
}
