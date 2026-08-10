package com.mware.experiment.mq.producer;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.mware.experiment.mq.message.RunnerTaskMessage;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class RunnerCancelTaskProducer {
    /** 定向取消交换机：按 instanceId 路由到持有该任务的 runner 实例（runner 侧 RunnerCancelRabbitConfig 声明） */
    private static final String CANCEL_EXCHANGE = "runner.cancel.exchange";
    /** 任务→实例登记键前缀（对齐 runner 侧 RunnerRedisKeys.TASK_INSTANCE_PREFIX；
     *  experiment 不引 runner 类，故按契约手写常量） */
    private static final String TASK_INSTANCE_PREFIX = "runner:task:instance:";

    private final RabbitTemplate cancelTaskRabbitTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    public RunnerCancelTaskProducer(ConnectionFactory conFactory,
            Jackson2JsonMessageConverter jackson2JsonMessageConverter,
            StringRedisTemplate stringRedisTemplate) {
        this.cancelTaskRabbitTemplate = new RabbitTemplate(conFactory);
        this.cancelTaskRabbitTemplate.setMessageConverter(jackson2JsonMessageConverter);
        // 消息发到 Exchange 后，如果找不到任何匹配的 Queue，不要直接把消息丢掉，而是把消息退回给生产者。
        this.cancelTaskRabbitTemplate.setMandatory(true);
        this.cancelTaskRabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                log.debug("task publisher confirm OK: taskId={}",
                        correlationData != null ? correlationData.getId() : "null");
            } else {
                log.error("task publisher confirm FAIL: taskId={}, cause={}",
                        correlationData != null ? correlationData.getId() : "null", cause);
                // TODO 补偿：confirm=false 说明 Broker 未持久化（交换机写入失败 / 集群异常），
                // 将 experiment_task 置回 PENDING 交由扫描重投，或走告警
            }
        });

        this.cancelTaskRabbitTemplate.setReturnsCallback(returned -> log.error(
                "task message returned (mandatory): exchange={}, routingKey={}, reply={}",
                returned.getExchange(), returned.getRoutingKey(), returned.getReplyText()));

        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void send(RunnerTaskMessage message) {
        CorrelationData correlationData = new CorrelationData(String.valueOf(message.getTaskId()));

        // 1. 定向取消：查 Redis 任务→实例登记（runner 侧 T4 登记），命中则发往持有该任务的实例
        String instanceId = lookupInstanceId(message.getTaskId());
        if (StringUtils.hasText(instanceId)) {
            cancelTaskRabbitTemplate.convertAndSend(CANCEL_EXCHANGE, instanceId, message, correlationData);
            log.debug("cancel sent to instance {}: taskId={}", instanceId, message.getTaskId());
            return;
        }

        // 2. 未命中（任务未登记 / 已结束 / 实例已下线 / Redis 不可用）：本地已无可取消的
        //    Future（任务结束后 execute 的 finally 会清登记），无需广播，直接丢弃并记日志。
        log.warn("cancel skipped: no registered instance for taskId={} (task finished or never created)",
                message.getTaskId());
    }

    /** 查询任务持有的 runner 实例 ID；Redis 异常时返回 null（此时调用方跳过发送） */
    private String lookupInstanceId(Long taskId) {
        if (taskId == null) {
            return null;
        }
        try {
            return stringRedisTemplate.opsForValue().get(TASK_INSTANCE_PREFIX + taskId);
        } catch (RuntimeException e) {
            log.warn("查询任务实例登记失败，跳过取消发送：taskId={}, cause={}", taskId, e.getMessage());
            return null;
        }
    }

}
