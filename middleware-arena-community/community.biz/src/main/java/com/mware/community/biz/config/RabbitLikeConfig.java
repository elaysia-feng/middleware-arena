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

@Configuration
@EnableRabbit
@Slf4j
public class RabbitLikeConfig {
    public static final String EXCHANGE_LIKE = "like.event.exchange";
    public static final String QUEUE_PERSIST = "like.persist.queue";
    public static final String QUEUE_STATISTICS = "like.statistics.queue";
    public static final String DLX = "like.dlx";
    public static final String DLQ = "like.dlq";

    @Bean public FanoutExchange likeEventExchange() { return new FanoutExchange(EXCHANGE_LIKE, true, false); }
    @Bean public Queue persistQueue() { return businessQueue(QUEUE_PERSIST); }
    @Bean public Queue statisticsQueue() { return businessQueue(QUEUE_STATISTICS); }
    @Bean public DirectExchange deadLetterExchange() { return new DirectExchange(DLX, true, false); }
    @Bean public Queue deadLetterQueue() { return QueueBuilder.durable(DLQ).build(); }
    @Bean public Binding persistBinding() { return BindingBuilder.bind(persistQueue()).to(likeEventExchange()); }
    @Bean public Binding statisticsBinding() { return BindingBuilder.bind(statisticsQueue()).to(likeEventExchange()); }
    @Bean public Binding dlqBinding() { return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange()).with("dead"); }

    private Queue businessQueue(String name) {
        return QueueBuilder.durable(name)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", "dead").build();
    }

    @Bean public Jackson2JsonMessageConverter likeJsonMessageConverter() { return new Jackson2JsonMessageConverter(); }

    @Bean
    public RabbitTemplate likeRabbitTemplate(ConnectionFactory connectionFactory, Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        template.setMandatory(true);
        template.setConfirmCallback((data, ack, cause) -> {
            if (!ack) log.error("publisher confirm failed: eventId={}, cause={}", data != null ? data.getId() : "null", cause);
        });
        template.setReturnsCallback(r -> log.error("message returned: exchange={}, routingKey={}, reply={}",
                r.getExchange(), r.getRoutingKey(), r.getReplyText()));
        return template;
    }

    @Bean
    public RetryOperationsInterceptor likeRetryInterceptor() {
        return RetryInterceptorBuilder.stateless().maxAttempts(3)
                .backOffOptions(500L, 2.0, 5000L)
                .recoverer(new RejectAndDontRequeueRecoverer()).build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, Jackson2JsonMessageConverter converter,
            RetryOperationsInterceptor likeRetryInterceptor) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(50);
        factory.setConcurrentConsumers(2);
        factory.setMaxConcurrentConsumers(8);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(likeRetryInterceptor);
        return factory;
    }
}
