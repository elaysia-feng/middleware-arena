package com.mware.experiment.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 实验任务链路 RabbitMQ 基础设施配置：只声明拓扑 + 消息转换，不含生产端模板。
 * <p>
 * 拓扑（direct 点对点投递任务）：
 * <pre>
 *                     RabbitMQ
 *   experiment.task.exchange (direct, durable)
 *          │ routingKey=runner.task
 *          ▼
 *      runner.task.queue      ← runner 契约队列（见 RunnerServiceImpl TODO），绑定 x-dead-letter-exchange → experiment.task.dlx → experiment.task.dlq
 * </pre>
 * <ul>
 *   <li><b>生产端可靠性</b>：Mandatory/Return + Publisher Confirm 是生产端模板的能力，
 *       由 {@code RunnerTaskProducer}（experiment.mq）构建自己的 RabbitTemplate 时配置，
 *       不放在本基础设施配置里（见 RunnerTaskProducer）。</li>
 *   <li><b>消息体</b>：{@code RunnerTaskMessage}（experiment.mq 模块，两端各自维护 JSON 契约）。</li>
 *   <li><b>投递入口</b>：业务侧注入 {@code RunnerTaskProducer}（experiment.mq），不直接用本模板。</li>
 *   <li><b>消费端</b>：runner 侧声明；本配置仅声明拓扑 + 消息转换器，
 *       预留消费进度回传通道待 runner 契约确定后补充。</li>
 * </ul>
 */
@Configuration
public class ExperimentRabbitConfig {

    /** 任务投递交换机：direct，routingKey 决定进哪个队列 */
    public static final String EXCHANGE_TASK = "experiment.task.exchange";
    /** runner 契约队列（对齐 RunnerServiceImpl 的 runner.task.queue） */
    public static final String QUEUE_RUNNER = "runner.task.queue";
    /** 绑定路由键 */
    public static final String ROUTING_KEY_TASK = "runner.task";
    /** 死信交换机 / 死信队列（重试耗尽后兜底） */
    public static final String DLX = "experiment.task.dlx";
    public static final String DLQ = "experiment.task.dlq";

    // ==================== 交换机 / 队列 / 绑定 ====================

    @Bean
    public DirectExchange taskExchange() {
        return new DirectExchange(EXCHANGE_TASK, true, false);
    }

    @Bean
    public Queue runnerTaskQueue() {
        // durable + 绑定死信交换机：消费失败（重试耗尽）→ experiment.task.dlq，避免消息丢失
        return QueueBuilder.durable(QUEUE_RUNNER)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", "dead")
                .build();
    }

    @Bean
    public DirectExchange taskDeadLetterExchange() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    public Queue taskDeadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Binding taskBinding() {
        return BindingBuilder.bind(runnerTaskQueue()).to(taskExchange()).with(ROUTING_KEY_TASK);
    }

    @Bean
    public Binding dlqBinding() {
        return BindingBuilder.bind(taskDeadLetterQueue()).to(taskDeadLetterExchange()).with("dead");
    }

    // ==================== 消息转换 ====================

    @Bean
    public Jackson2JsonMessageConverter taskJsonMessageConverter() {
        // convertAndSend(RunnerTaskMessage) 自动序列化为 JSON；
        // 与 runner 消费侧同用 Jackson2JsonMessageConverter，字段一一对应
        return new Jackson2JsonMessageConverter();
    }
}
