package com.mware.experiment.mq.producer;

import com.mware.experiment.config.ExperimentRabbitConfig;
import com.mware.experiment.mq.message.RunnerTaskMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.ObjectProvider;
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
    /** 投递失败补偿钩子：mq 模块不碰数据库，落库补偿由 biz 实现（ObjectProvider 解耦，可为空） */
    private final ObjectProvider<RunnerDispatchCompensator> dispatchCompensator;

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
            Jackson2JsonMessageConverter taskJsonMessageConverter,
            ObjectProvider<RunnerDispatchCompensator> dispatchCompensator) {
        this.createTaskRabbitTemplate = new RabbitTemplate(connectionFactory);
        this.dispatchCompensator = dispatchCompensator;
        // 拿到消息的时候用 json 序列化，而不是 Java 的序列化
        this.createTaskRabbitTemplate.setMessageConverter(taskJsonMessageConverter);
        // Mandatory：消息不可路由时触发 ReturnsCallback，配合 spring.rabbitmq.publisher-returns=true
        this.createTaskRabbitTemplate.setMandatory(true);
        // Publisher Confirm：配合 spring.rabbitmq.publisher-confirm-type=correlated。
        // correlationData 携带 taskId，confirm=false 时触发补偿（experiment_task 置 FAILED 可重试），
        // 覆盖"confirm 晚于 send() 超时返回"的窗口；send() 同步路径的失败也走同一补偿。
        this.createTaskRabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                log.debug("task publisher confirm OK: taskId={}",
                        correlationData != null ? correlationData.getId() : "null");
            } else {
                log.error("task publisher confirm FAIL: taskId={}, cause={}",
                        correlationData != null ? correlationData.getId() : "null", cause);
                compensate(correlationData, cause);
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
     * 将 experiment_task 标记为已投递；confirm=false 时触发
     * {@link RunnerDispatchCompensator#onDispatchFailed}（experiment_task 置 FAILED，用户可重试）。
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
                String reason = confirm.isAck()
                        ? "message returned (unroutable)" : confirm.getReason();
                compensate(correlationData, reason);
                throw new IllegalStateException("Runner 任务投递失败，taskId=" + message.getTaskId()
                        + ", cause=" + reason);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            compensate(correlationData, "interrupted while waiting confirm");
            throw new IllegalStateException("等待 Runner 任务投递确认时被中断，taskId=" + message.getTaskId(), e);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            compensate(correlationData, e.getClass().getSimpleName() + " while waiting confirm");
            throw new IllegalStateException("等待 Runner 任务投递确认失败，taskId=" + message.getTaskId(), e);
        }
        log.debug("RunnerTaskMessage sent: taskId={}, tier={}", message.getTaskId(), message.getTier());
    }

    /** confirm 失败 / 超时的统一补偿入口；补偿实现必须幂等 */
    private void compensate(CorrelationData correlationData, String reason) {
        if (correlationData == null || correlationData.getId() == null) {
            return;
        }
        dispatchCompensator.ifAvailable(compensator -> {
            try {
                compensator.onDispatchFailed(Long.valueOf(correlationData.getId()), "CREATE", reason);
            } catch (RuntimeException e) {
                // 补偿失败不能再抛：confirm 回调里抛异常会打断 RabbitMQ 客户端线程
                log.error("task dispatch compensation failed: taskId={}", correlationData.getId(), e);
            }
        });
    }
}
