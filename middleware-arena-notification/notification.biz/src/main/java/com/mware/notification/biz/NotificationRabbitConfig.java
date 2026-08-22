package com.mware.notification.biz;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 实验完成通知的 RabbitMQ 拓扑。 */
@Configuration
public class NotificationRabbitConfig {
    public static final String EXCHANGE = "experiment.completed.exchange";
    public static final String QUEUE = "notification.experiment.completed.queue";
    public static final String ROUTING_KEY = "experiment.completed";

    @Bean
    DirectExchange experimentCompletedExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    Queue experimentCompletedQueue() {
        return new Queue(QUEUE, true);
    }

    @Bean
    Binding experimentCompletedBinding(Queue experimentCompletedQueue, DirectExchange experimentCompletedExchange) {
        return BindingBuilder.bind(experimentCompletedQueue).to(experimentCompletedExchange).with(ROUTING_KEY);
    }
}
