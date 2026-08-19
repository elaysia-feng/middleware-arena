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
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

/**
 * 点赞 RabbitMQ 配置。
 * <p>
 * like.event.exchange 使用 Fanout：一条点赞事件同时投递给持久化消费者和统计消费者；
 * 两个业务队列都绑定 DLX，消费重试耗尽后进入 like.dlq，避免无限 requeue。
 */
@Configuration
@EnableRabbit
@Slf4j
public class RabbitLikeConfig {

    public static final String EXCHANGE_LIKE = "like.event.exchange";
    public static final String QUEUE_PERSIST = "like.persist.queue";
    public static final String QUEUE_STATISTICS = "like.statistics.queue";
    public static final String DLX = "like.dlx";
    public static final String DLQ = "like.dlq";

    @Bean
    public FanoutExchange likeEventExchange() {
        // 点赞事件需要同时触发 MySQL 持久化和热度统计，因此使用 Fanout Exchange。
        return new FanoutExchange(EXCHANGE_LIKE, true, false);
    }

    @Bean
    public Queue persistQueue() {
        return businessQueue(QUEUE_PERSIST);
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
    public Binding persistBinding() {
        return BindingBuilder.bind(persistQueue()).to(likeEventExchange());
    }

    @Bean
    public Binding statisticsBinding() {
        return BindingBuilder.bind(statisticsQueue()).to(likeEventExchange());
    }

    @Bean
    public Binding dlqBinding() {
        return BindingBuilder.bind(deadLetterQueue())
                .to(deadLetterExchange())
                .with("dead");
    }

    /**
     * 两个业务队列统一配置死信交换机，Retry 耗尽后消息进入 DLQ。
     */
    private Queue businessQueue(String name) {
        return QueueBuilder.durable(name)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", "dead")
                .build();
    }

    @Bean
    public Jackson2JsonMessageConverter likeJsonMessageConverter() {
        // LikeEvent 统一按 JSON 发布/消费，避免生产者和消费者手写序列化逻辑。
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate likeRabbitTemplate(ConnectionFactory connectionFactory,
                                             Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);

        //   1. mandatory=true：Exchange 找不到可路由 Queue 时触发 ReturnsCallback，而不是静默丢消息。
        template.setMandatory(true);

        //   2. Publisher Confirm：Broker 是否成功接收消息。
        template.setConfirmCallback((data, ack, cause) -> {
            if (!ack) {
                log.error(
                        "publisher confirm failed: eventId={}, cause={}",
                        data != null ? data.getId() : "null",
                        cause);
            }
        });

        //   3. Publisher Return：Broker 收到了消息，但没有成功路由到任何 Queue。
        template.setReturnsCallback(returned -> log.error(
                "message returned: exchange={}, routingKey={}, reply={}",
                returned.getExchange(),
                returned.getRoutingKey(),
                returned.getReplyText()));

        return template;
    }

    @Bean
    public RetryOperationsInterceptor likeRetryInterceptor() {
        // 消费失败：最多尝试 3 次，500ms 起步指数退避，最长 5s；仍失败则拒绝并进入 DLQ。
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(500L, 2.0, 5000L)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter converter,
            RetryOperationsInterceptor likeRetryInterceptor) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);

        //   1. 手动 ACK：只有 MySQL / Redis 统计真正成功后，消费者代码才确认消息。
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);

        //   2. 单消费者最多预取 50 条，避免消费者一次抓取过多未 ACK 消息。
        factory.setPrefetchCount(50);

        //   3. 根据积压量在 2~8 个消费者之间扩缩，控制数据库和 Redis 的下游压力。
        factory.setConcurrentConsumers(2);
        factory.setMaxConcurrentConsumers(8);

        //   4. Retry 耗尽后不重新入原队列，由 DLX/DLQ 接管失败消息。
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(likeRetryInterceptor);

        return factory;
    }
}
