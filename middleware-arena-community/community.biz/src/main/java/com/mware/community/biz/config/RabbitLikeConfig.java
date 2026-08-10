package com.mware.community.biz.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 点赞链路 RabbitMQ 基础设施。
 * <p>
 * 拓扑（fanout 一条消息多队列消费）：
 * <pre>
 *                  RabbitMQ
 *      like.event.exchange (fanout, durable)
 *          /        |        \
 *   count.queue  cache.queue  statistics.queue   ← 各绑一个队列，绑定 x-dead-letter-exchange → like.dlx → like.dlq
 * </pre>
 * <ul>
 *   <li><b>生产端可靠性</b>：Transactional Outbox 保证"DB 成功 ≠ MQ 必达"（outbox 兜底重投）；
 *       Publisher Confirm 确认 Broker 已持久化；Mandatory/Return 兜底"消息不可路由"。</li>
 *   <li><b>Broker</b>：Durable 交换机 / 队列 / 持久化消息；Quorum Queue 实验见 {@link #businessQueue} 注释。</li>
 *   <li><b>消费端</b>：MANUAL ACK + 重试（spring.rabbitmq.listener.simple.retry，见 application.yml）
 *       + 重试耗尽走死信 → DLQ；消费者幂等由 consumer_event 表保证（ConsumerIdempotency）。</li>
 * </ul>
 */
@Configuration
@EnableRabbit
@Slf4j
public class RabbitLikeConfig {

    public static final String EXCHANGE_LIKE = "like.event.exchange";
    public static final String QUEUE_COUNT = "like.count.queue";
    public static final String QUEUE_CACHE = "like.cache.queue";
    public static final String QUEUE_STATISTICS = "like.statistics.queue";
    public static final String DLX = "like.dlx";
    public static final String DLQ = "like.dlq";

    // ==================== 交换机 / 队列 / 绑定 ====================

    @Bean
    public FanoutExchange likeEventExchange() {
        // fanout：忽略 routingKey，一条消息广播到所有绑定队列
        return new FanoutExchange(EXCHANGE_LIKE, true, false);
    }

    @Bean
    public Queue countQueue() {
        return businessQueue(QUEUE_COUNT);
    }

    @Bean
    public Queue cacheQueue() {
        return businessQueue(QUEUE_CACHE);
    }

    @Bean
    public Queue statisticsQueue() {
        return businessQueue(QUEUE_STATISTICS);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Binding countBinding() {
        return BindingBuilder.bind(countQueue()).to(likeEventExchange());
    }

    @Bean
    public Binding cacheBinding() {
        return BindingBuilder.bind(cacheQueue()).to(likeEventExchange());
    }

    @Bean
    public Binding statisticsBinding() {
        return BindingBuilder.bind(statisticsQueue()).to(likeEventExchange());
    }

    @Bean
    public Binding dlqBinding() {
        return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange()).with("dead");
    }

    /**
     * 业务队列：durable + 绑定死信交换机（重试耗尽 → 进 like.dlq）。
     * <p>
     * <b>Quorum Queue 实验</b>（高可用 + 数据安全，镜像队列替代品）：
     * 把 {@code QueueBuilder.durable(name)} 换成
     * {@code QueueBuilder.durable(name).withArgument("x-queue-type", "quorum")}。
     * 注意：quorum 队列要求 RabbitMQ 3.8+，且不兼容部分 classic 特性（如消息 TTL 场景需确认）。
     */
    private Queue businessQueue(String name) {
        return QueueBuilder.durable(name)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", "dead")
                .build();
    }

    // ==================== 消息转换 ====================

    @Bean
    public Jackson2JsonMessageConverter likeJsonMessageConverter() {
        // 生产 / 消费统一 JSON：convertAndSend(LikeEvent) 自动序列化，
        // @RabbitListener 方法参数 LikeEvent 自动反序列化
        return new Jackson2JsonMessageConverter();
    }

    // ==================== 生产端：RabbitTemplate ====================

    @Bean
    public RabbitTemplate likeRabbitTemplate(ConnectionFactory connectionFactory,
                                             Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        // Mandatory：消息不可路由时触发 ReturnsCallback，配合 spring.rabbitmq.publisher-returns=true
        template.setMandatory(true);
        // Publisher Confirm：配合 spring.rabbitmq.publisher-confirm-type=correlated。
        // 异步回调，correlationData 携带 eventId，可据此标记 outbox 为 SENT（见 OutboxRelay）
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                log.debug("publisher confirm OK: eventId={}",
                        correlationData != null ? correlationData.getId() : "null");
            } else {
                log.error("publisher confirm FAIL: eventId={}, cause={}",
                        correlationData != null ? correlationData.getId() : "null", cause);
                // TODO 补偿：confirm=false 说明 Broker 未持久化（交换机写入失败 / 集群异常），
                //   回捞 event_outbox 该 eventId 置 FAILED 由扫描重投，或走告警
            }
        });
        template.setReturnsCallback(returned -> log.error(
                "message returned (mandatory): exchange={}, routingKey={}, reply={}, body={}",
                returned.getExchange(), returned.getRoutingKey(),
                returned.getReplyText(), new String(returned.getMessage().getBody())));
        return template;
    }

    // ==================== 消费端：监听容器工厂 ====================

    /**
     * 点赞消费者监听容器工厂：手动 ACK + 预取 + 并发。
     * <p>
     * spring.rabbitmq.listener.simple.retry.*（application.yml，默认注释）由 Boot 的
     * ListenerContainerFactoryConfigurer 自动套用到本工厂：消费抛异常 → 重试 N 次 →
     * 仍失败 defaultRequeueRejected=false → 进 x-dead-letter-exchange → like.dlq。
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, Jackson2JsonMessageConverter converter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(10);
        factory.setConcurrentConsumers(2);
        factory.setMaxConcurrentConsumers(8);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
