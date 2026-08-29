package com.mware.experiment.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Experiment -> Python Agent 的 RabbitMQ 拓扑契约。
 *
 * <p>1. 本类声明 Java 生产端使用的 exchange / queue / routing-key / DLX。</p>
 * <p>2. Python 端 app/mq/constants.py 必须与这里完全一致，包括 durable 和 queue arguments。</p>
 * <p>3. 自动分析走 MQ；用户主动 analyze/patch/compare 仍可经 Gateway -> FastAPI HTTP。</p>
 * <p>4. 生产端 Confirm + Mandatory 见 {@code AgentAnalysisTaskProducer}（已接入）；
 *    状态消费者见 {@code AgentAnalysisStatusConsumer}（MANUAL ack + 幂等落库，毒消息丢弃、
 *    瞬时故障 requeue，状态队列定稿不建独立 DLQ）。
 *    两端拓扑字符串由 {@code AgentMqContractTest} 契约测试守护（存在 Python 仓库时自动比对）。</p>
 */
@Configuration
public class AgentRabbitConfig {

    public static final String EXCHANGE_ANALYSIS = "agent.analysis.exchange";
    public static final String QUEUE_ANALYSIS = "agent.analysis.queue";
    public static final String ROUTING_KEY_ANALYSIS = "agent.analysis";

    public static final String DLX_ANALYSIS = "agent.analysis.dlx";
    public static final String DLQ_ANALYSIS = "agent.analysis.dlq";
    public static final String ROUTING_KEY_DEAD = "dead";

    public static final String EXCHANGE_STATUS = "agent.analysis.status.exchange";
    public static final String QUEUE_STATUS = "agent.analysis.status.queue";
    public static final String ROUTING_KEY_STATUS = "agent.analysis.status";

    @Bean
    public DirectExchange agentAnalysisExchange() {
        return new DirectExchange(EXCHANGE_ANALYSIS, true, false);
    }

    @Bean
    public Queue agentAnalysisQueue() {
        return QueueBuilder.durable(QUEUE_ANALYSIS)
                .withArgument("x-dead-letter-exchange", DLX_ANALYSIS)
                .withArgument("x-dead-letter-routing-key", ROUTING_KEY_DEAD)
                .build();
    }

    @Bean
    public Binding agentAnalysisBinding() {
        return BindingBuilder.bind(agentAnalysisQueue())
                .to(agentAnalysisExchange())
                .with(ROUTING_KEY_ANALYSIS);
    }

    @Bean
    public DirectExchange agentAnalysisDeadLetterExchange() {
        return new DirectExchange(DLX_ANALYSIS, true, false);
    }

    @Bean
    public Queue agentAnalysisDeadLetterQueue() {
        return QueueBuilder.durable(DLQ_ANALYSIS).build();
    }

    @Bean
    public Binding agentAnalysisDeadLetterBinding() {
        return BindingBuilder.bind(agentAnalysisDeadLetterQueue())
                .to(agentAnalysisDeadLetterExchange())
                .with(ROUTING_KEY_DEAD);
    }

    @Bean
    public DirectExchange agentAnalysisStatusExchange() {
        return new DirectExchange(EXCHANGE_STATUS, true, false);
    }

    @Bean
    public Queue agentAnalysisStatusQueue() {
        return QueueBuilder.durable(QUEUE_STATUS).build();
    }

    @Bean
    public Binding agentAnalysisStatusBinding() {
        return BindingBuilder.bind(agentAnalysisStatusQueue())
                .to(agentAnalysisStatusExchange())
                .with(ROUTING_KEY_STATUS);
    }
}
