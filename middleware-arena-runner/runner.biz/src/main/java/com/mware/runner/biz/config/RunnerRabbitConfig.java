package com.mware.runner.biz.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Runner 侧 RabbitMQ 基础设施：消费端拓扑 + 消息转换 + 监听容器工厂。
 * <p>
 * 线程池独立在 {@link RunnerTaskExecutorConfig}（{@code runnerTaskExecutor}），
 * 这里只通过 {@code @Qualifier} 引用，不在本类里重复定义。
 * <p>
 * 拓扑（与 experiment-service 的 {@code ExperimentRabbitConfig} 同名同参，RabbitMQ
 * 启动时幂等合并）：
 *
 * <pre>
 *                     RabbitMQ
 *   experiment.task.exchange (direct, durable)
 *          │ routingKey=runner.task
 *          ▼
 *      runner.task.queue       ← runner 契约队列；绑定 x-dead-letter-exchange → experiment.task.dlx → experiment.task.dlq
 * </pre>
 * <ul>
 * <li><b>生产端在 experiment-service</b>（RunnerCreaetTaskProducer /
 * RunnerCancelTaskProducer），
 * 本服务只声明消费侧拓扑 + 消息转换器 + 监听容器。</li>
 * <li><b>消息体</b>：{@code RunnerTaskMessage}（runner.dto，两端各自维护 JSON 契约）。</li>
 * <li><b>消费可靠性</b>：手动 ACK +
 * spring.rabbitmq.listener.simple.retry.*（application.yml）+ 重试耗尽
 * defaultRequeueRejected=false → 走 x-dead-letter-exchange →
 * experiment.task.dlq。</li>
 * </ul>
 */
@Configuration
@EnableRabbit
@Slf4j
public class RunnerRabbitConfig {

    /** 任务投递交换机：与 experiment-service 同名同参，幂等合并 */
    public static final String EXCHANGE_TASK = "experiment.task.exchange";
    /** runner 契约队列：experiment-service / runner-service 两端各自声明同样的队列名与参数 */
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
        // 与 experiment-service 同用 Jackson2JsonMessageConverter，RunnerTaskMessage 字段一一对应
        return new Jackson2JsonMessageConverter();
    }

    // ==================== 消费端：监听容器工厂 ====================

    /**
     * runner 消费者监听容器工厂：手动 ACK + 预取 + 自定义线程池 + 重试耗尽走死信。
     * <p>
     * spring.rabbitmq.listener.simple.retry.*（application.yml，默认注释）由 Boot 的
     * ListenerContainerFactoryConfigurer 自动套用到本工厂：消费抛异常 → 重试 N 次 →
     * 仍失败 defaultRequeueRejected=false → 进 x-dead-letter-exchange →
     * experiment.task.dlq。
     * <p>
     * 绑定的线程池见 {@link RunnerTaskExecutorConfig#runnerTaskExecutor()}；其
     * corePoolSize / maxPoolSize 自动充当并发消费者上下限，故不重复设置
     * setConcurrentConsumers / setMaxConcurrentConsumers。
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter converter,
            @Qualifier(RunnerTaskExecutorConfig.BEAN_NAME) ThreadPoolTaskExecutor executor) {

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();

        // 先把 application.yml 的配置灌进来
        configurer.configure(factory, connectionFactory);

        // 再覆盖本服务的特殊配置
        factory.setMessageConverter(converter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        // prefetch(预取) 取到 executor 核心并发，避免 prefetch 太少导致线程空转；
        // 同时不会比 maxPoolSize 大很多（≤ maxPoolSize 才不会在客户端积攒超额消息）。
        factory.setPrefetchCount(executor.getCorePoolSize());
        // 用自定义线程池替代默认 SimpleAsyncTaskExecutor（每请求一线程，无回收）
        factory.setTaskExecutor(executor);
        // 重试耗尽后不回滚原队列，走 x-dead-letter-exchange → experiment.task.dlq
        factory.setDefaultRequeueRejected(false);

        return factory;
    }
}
