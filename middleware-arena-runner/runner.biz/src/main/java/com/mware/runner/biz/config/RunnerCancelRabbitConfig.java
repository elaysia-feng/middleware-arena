package com.mware.runner.biz.config;

import com.mware.runner.biz.execution.InstanceInfo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Runner 定向取消（per-instance）RabbitMQ 基础设施：每实例一个专属取消队列。
 * <p>
 * experiment-service 发起取消时，先查 Redis 任务→实例登记（{@code runner:task:instance:{taskId}}，
 * 见 {@link RunnerRedisKeys}）拿到持有该任务的 runner 实例 ID，然后向 {@code runner.cancel.exchange}
 * 以 instanceId 为 routingKey 定向投递；只有持有该任务的实例（其专属队列绑定同 routingKey）能收到。
 * <p>
 * 拓扑（每个 runner 实例启动时各自声明）：
 * <pre>
 *            RabbitMQ
 *  runner.cancel.exchange (direct, durable)
 *          │ routingKey=instanceId
 *          ▼
 *  runner.cancel.{instanceId}   ← autoDelete=true：实例下线、最后一个消费者断开时 RabbitMQ
 *                                  自动删除队列，避免实例重启（UUID 变化）后旧队列残留
 * </pre>
 * <p>
 * 消费端见 {@link com.mware.runner.biz.consumer.CancelConsumer}（@RabbitListener 引用
 * {@code #{cancelQueue.name}}）。
 */
@Configuration
@Slf4j
public class RunnerCancelRabbitConfig {

    /** 定向取消交换机：experiment 侧按 instanceId 路由到持有该任务的 runner 实例 */
    public static final String CANCEL_EXCHANGE = "runner.cancel.exchange";
    /** 每实例取消队列前缀：runner.cancel.{instanceId} */
    public static final String CANCEL_QUEUE_PREFIX = "runner.cancel.";

    private final InstanceInfo instanceInfo;

    public RunnerCancelRabbitConfig(InstanceInfo instanceInfo) {
        this.instanceInfo = instanceInfo;
    }

    // ==================== 交换机 / 队列 / 绑定 ====================

    @Bean
    public DirectExchange cancelExchange() {
        return new DirectExchange(CANCEL_EXCHANGE, true, false);
    }

    @Bean
    public Queue cancelQueue() {
        // durable + autoDelete（非排他）：实例下线、最后一个消费者断开后由 RabbitMQ 自动删除，
        // 避免实例重启 UUID 变化后旧队列残留。
        String queueName = CANCEL_QUEUE_PREFIX + instanceInfo.getInstanceId();
        log.info("runner 声明定向取消队列：{}", queueName);
        return QueueBuilder.durable(queueName).autoDelete().build();
    }

    @Bean
    public Binding cancelBinding() {
        return BindingBuilder.bind(cancelQueue()).to(cancelExchange()).with(instanceInfo.getInstanceId());
    }
}
