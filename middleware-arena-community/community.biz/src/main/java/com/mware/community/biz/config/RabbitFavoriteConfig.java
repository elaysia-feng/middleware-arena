package com.mware.community.biz.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 收藏事件 RabbitMQ 拓扑。
 * <p>
 * 收藏使用独立交换机和持久化队列；JSON 转换器、消费者并发、重试策略和 DLX
 * 复用 {@link RabbitLikeConfig} 中的统一监听器配置。
 */
@Configuration
public class RabbitFavoriteConfig {
    public static final String EXCHANGE_FAVORITE = "favorite.event.exchange";
    public static final String QUEUE_PERSIST = "favorite.persist.queue";

    @Bean
    public FanoutExchange favoriteEventExchange() {
        return new FanoutExchange(EXCHANGE_FAVORITE, true, false);
    }

    @Bean
    public Queue favoritePersistQueue() {
        // 消费重试耗尽后拒绝原消息，由统一 like.dlx 路由到 like.dlq 留待人工处理。
        return QueueBuilder.durable(QUEUE_PERSIST)
                .withArgument("x-dead-letter-exchange", RabbitLikeConfig.DLX)
                .withArgument("x-dead-letter-routing-key", "dead")
                .build();
    }

    @Bean
    public Binding favoritePersistBinding() {
        return BindingBuilder.bind(favoritePersistQueue()).to(favoriteEventExchange());
    }
}
