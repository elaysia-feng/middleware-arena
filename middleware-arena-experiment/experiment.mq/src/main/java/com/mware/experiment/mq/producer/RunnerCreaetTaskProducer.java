package com.mware.experiment.mq.producer;

import com.mware.experiment.config.ExperimentRabbitConfig;
import com.mware.experiment.mq.message.RunnerTaskMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Runner 任务消息生产端：experiment → {@code experiment.task.exchange} →
 * {@code runner.task.queue}。
 * <p>
 * 生产端模板由本类自建：Mandatory/Return + Publisher Confirm 是<b>生产端</b>的能力，归生产者管，
 * 不放在基础设施配置 {@link ExperimentRabbitConfig}（那里只声明拓扑 + 消息转换器）。
 * 业务侧（experiment.biz）只需注入本 Producer 调 {@link #send(RunnerTaskMessage)}，
 * 不用关心交换机 / 路由键 / 确认回调等细节。
 */
@Component
@Slf4j
public class RunnerCreaetTaskProducer {

    private final RabbitTemplate createTaskRabbitTemplate;

    /**
     * 自建生产端模板（注入 Spring Boot 自动配置的 ConnectionFactory，非 new 一个连接）。
     * <p>
     * 与 yml 的配合：
     * <ul>
     * <li>{@code publisher-confirm-type=correlated} → ConnectionFactory 开启
     * correlated confirm，
     * 本模板即可用 {@link CorrelationData} 拿 ACK/NACK。</li>
     * <li>{@code publisher-returns=true} → ConnectionFactory 开启 return 通道，
     * 配合 {@code setMandatory(true)}，消息不可路由时触发 ReturnsCallback。</li>
     * </ul>
     */
    public RunnerCreaetTaskProducer(ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter taskJsonMessageConverter) {
        this.createTaskRabbitTemplate = new RabbitTemplate(connectionFactory);
        // 拿到消息的时候用 json 序列化，而不是 Java 的序列化
        this.createTaskRabbitTemplate.setMessageConverter(taskJsonMessageConverter);
        // Mandatory：消息不可路由时触发 ReturnsCallback，配合 spring.rabbitmq.publisher-returns=true
        this.createTaskRabbitTemplate.setMandatory(true);
        // Publisher Confirm：配合 spring.rabbitmq.publisher-confirm-type=correlated。
        // correlationData 携带 taskId，可据此将 experiment_task 标记为已投递 / 回捞重投
        this.createTaskRabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                log.debug("task publisher confirm OK: taskId={}",
                        correlationData != null ? correlationData.getId() : "null");
            } else {
                log.error("task publisher confirm FAIL: taskId={}, cause={}",
                        correlationData != null ? correlationData.getId() : "null", cause);
            }
        });
        // 只打元数据，不打消息 body：新消息只含 OSS 引用和运行参数，历史消息可能仍含 filesJson，
        // 不可路由时整包打印会把敏感内容泄露进日志
        this.createTaskRabbitTemplate.setReturnsCallback(returned -> log.error(
                "task message returned (mandatory): exchange={}, routingKey={}, reply={}",
                returned.getExchange(), returned.getRoutingKey(), returned.getReplyText()));
    }

    /**
     * 投递任务消息到 runner 队列。
     * <p>
     * correlationData 携带 taskId：Broker 持久化成功后 ConfirmCallback（ACK）里可据此
     * 将 experiment_task 标记为已投递；confirm=false 时由补偿逻辑回捞重投（TODO 见本类）。
     *
     * @param message 任务消息（taskId / versionId / OSS 文件引用 / runParamsJson）
     */
    public void send(RunnerTaskMessage message) {
        CorrelationData correlationData = new CorrelationData(String.valueOf(message.getTaskId()));
        String routingKey = "VIP".equalsIgnoreCase(message.getTier())
                ? ExperimentRabbitConfig.ROUTING_KEY_VIP
                : ExperimentRabbitConfig.ROUTING_KEY_FREE;
        createTaskRabbitTemplate.convertAndSend(ExperimentRabbitConfig.EXCHANGE_TASK,
                routingKey, message, correlationData);

        // Broker ACK 且消息没有被 mandatory return，才表示任务真正进入对应队列。
        try {
            CorrelationData.Confirm confirm = correlationData.getFuture().get(10, TimeUnit.SECONDS);
            if (!confirm.isAck() || correlationData.getReturned() != null) {
                throw new IllegalStateException("Runner 任务投递失败，taskId=" + message.getTaskId()
                        + ", cause=" + confirm.getReason());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待 Runner 任务投递确认时被中断，taskId=" + message.getTaskId(), e);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            throw new IllegalStateException("等待 Runner 任务投递确认失败，taskId=" + message.getTaskId(), e);
        }
        log.debug("RunnerTaskMessage sent: taskId={}, tier={}", message.getTaskId(), message.getTier());
    }
}
