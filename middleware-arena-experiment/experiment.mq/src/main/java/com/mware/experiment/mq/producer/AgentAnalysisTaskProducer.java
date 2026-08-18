package com.mware.experiment.mq.producer;

import com.mware.experiment.config.AgentRabbitConfig;
import com.mware.experiment.mq.message.AgentAnalysisTaskMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Experiment -> Python Agent 自动分析任务生产者。
 *
 * <p>1. 投递到 agent.analysis.exchange / agent.analysis。</p>
 * <p>2. analysisId 作为 CorrelationData.id，便于定位投递失败。</p>
 * <p>3. 与 Runner producer 一样使用 Mandatory Return + Publisher Confirm。</p>
 *
 * TODO[业务]:
 * 1. Runner SUCCESS 且 experiment_result 落库后创建 experiment_analysis(CREATED)。
 * 2. send 成功后再将 analysis 状态推进 CREATED -> QUEUED。
 * 3. confirm 失败的 analysis 由补偿任务回捞，不在 Producer 内写数据库业务。
 */
@Component
@Slf4j
public class AgentAnalysisTaskProducer {

    private final RabbitTemplate rabbitTemplate;

    public AgentAnalysisTaskProducer(ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter taskJsonMessageConverter) {
        this.rabbitTemplate = new RabbitTemplate(connectionFactory);
        this.rabbitTemplate.setMessageConverter(taskJsonMessageConverter);
        this.rabbitTemplate.setMandatory(true);
        this.rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                log.debug("agent analysis publisher confirm OK: analysisId={}",
                        correlationData != null ? correlationData.getId() : "null");
            } else {
                log.error("agent analysis publisher confirm FAIL: analysisId={}, cause={}",
                        correlationData != null ? correlationData.getId() : "null", cause);
            }
        });
        this.rabbitTemplate.setReturnsCallback(returned -> log.error(
                "agent analysis message returned: exchange={}, routingKey={}, reply={}",
                returned.getExchange(), returned.getRoutingKey(), returned.getReplyText()));
    }

    public void send(AgentAnalysisTaskMessage message) {
        if (message == null || message.getAnalysisId() == null || message.getTaskId() == null) {
            throw new IllegalArgumentException("AgentAnalysisTaskMessage 缺少 analysisId/taskId");
        }

        CorrelationData correlationData = new CorrelationData(String.valueOf(message.getAnalysisId()));
        rabbitTemplate.convertAndSend(
                AgentRabbitConfig.EXCHANGE_ANALYSIS,
                AgentRabbitConfig.ROUTING_KEY_ANALYSIS,
                message,
                correlationData);

        try {
            CorrelationData.Confirm confirm = correlationData.getFuture().get(10, TimeUnit.SECONDS);
            if (!confirm.isAck() || correlationData.getReturned() != null) {
                throw new IllegalStateException("Agent 分析任务投递失败，analysisId=" + message.getAnalysisId()
                        + ", cause=" + confirm.getReason());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待 Agent 分析任务 Confirm 时被中断，analysisId="
                    + message.getAnalysisId(), e);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            throw new IllegalStateException("等待 Agent 分析任务 Confirm 失败，analysisId="
                    + message.getAnalysisId(), e);
        }
    }
}
