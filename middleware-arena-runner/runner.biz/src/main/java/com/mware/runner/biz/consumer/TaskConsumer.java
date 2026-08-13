package com.mware.runner.biz.consumer;

import com.mware.runner.biz.config.RunnerRabbitConfig;
import com.mware.runner.biz.config.ResourceBusyException;
import com.mware.runner.biz.execution.RunnerService;
import com.mware.runner.biz.progress.RunnerTaskStatusProducer;
import com.mware.runner.dto.RunnerTaskMessage;
import com.mware.runner.dto.RunnerTaskStatusMessage;

import com.rabbitmq.client.Channel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * runner 任务队列消费者（runner.task.queue）→ 接收 experiment-service 投递的
 * RunnerTaskMessage。
 * <p>
 * 当前是 createTask 框架：手动 ACK + 打印负载确认链路通；build → run → benchmark → collect →
 * cleanup 流水线的实际调用（{@code RunnerService.execute}）待 RunnerServiceImpl 落地后接入。
 * <p>
 * 消费可靠性（与 community 消费者同款）：
 * <ul>
 * <li>成功 → basicAck（手动确认，确认前宕机消息重派）</li>
 * <li>业务异常 → 不 ACK、异常向上抛 → spring.rabbitmq.listener.simple.retry 重试（默认 3 次）→
 * 耗尽后 defaultRequeueRejected=false → 进 x-dead-letter-exchange →
 * experiment.task.dlq</li>
 * </ul>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TaskConsumer {

    private final RunnerService runnerService;
    private final RunnerTaskStatusProducer taskStatusProducer;

    @RabbitListener(queues = RunnerRabbitConfig.QUEUE_VIP,
            containerFactory = "vipRabbitListenerContainerFactory", ackMode = "MANUAL")
    public void onVipTask(RunnerTaskMessage message, Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        consume(message, channel, deliveryTag);
    }

    @RabbitListener(queues = RunnerRabbitConfig.QUEUE_FREE,
            containerFactory = "freeRabbitListenerContainerFactory", ackMode = "MANUAL")
    public void onFreeTask(RunnerTaskMessage message, Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        consume(message, channel, deliveryTag);
    }

    private void consume(RunnerTaskMessage message, Channel channel, long deliveryTag) throws IOException {
        log.info("runner 收到任务：taskId={}, versionId={}, filesObjectKey={}, legacyFilesLen={}, paramsLen={}",
                message.getTaskId(), message.getVersionId(),
                message.getFilesObjectKey(),
                message.getFilesJson() == null ? 0 : message.getFilesJson().length(),
                message.getRunParamsJson() == null ? 0 : message.getRunParamsJson().length());

        try {
            // 0. 确定性坏消息（缺 taskId / 非 CREATE）重试也必失败，直接 NACK 进死信，不浪费重试次数
            if (message.getTaskId() == null) {
                log.warn("runner 收到缺 taskId 的脏消息，转入死信队列：message={}", message);
                channel.basicNack(deliveryTag, false, false);
                return;
            }

            // 本队列（runner.task.queue）只承载 CREATE 创建任务；
            // CANCEL 已由 CancelConsumer 走 per-instance 定向队列处理，不再路由到本队列。
            String taskType = message.getTaskType();
            if ("CREATE".equals(taskType)) {
                log.info("runner 收到创建任务：taskId={}", message.getTaskId());
                // T4：接入 RunnerService.execute，在线程池中异步执行
                // build → run → benchmark → collect 流水线，并完成 Future / Redis 登记。
                // 说明：execute 按现有接口签名为 RunnerTaskMessage，传完整 message，
                // taskId 由 execute 内部取用做 Future / Redis 键。
                runnerService.execute(message);

            } else {
                // 非 CREATE（含误路由的 CANCEL / 未知 / 空 taskType）：契约破坏，
                // 重试也失败，直接 NACK 进死信（runner.task.queue 已绑定
                // x-dead-letter-exchange → experiment.task.dlq）
                log.warn("runner 收到非 CREATE 消息，转入死信队列：taskId={}, taskType={}",
                        message.getTaskId(), taskType);
                channel.basicNack(deliveryTag, false, false);
                return;
            }

            // 流水线状态回传由 experiment-service 通过 SSE / Database 持有，
            // runner 仅负责执行 + 回传 progress / result 消息。
            channel.basicAck(deliveryTag, false);
        } catch (ResourceBusyException e) {
            // 资源等待已经达到该等级的业务上限，不再重试占用 RabbitMQ。
            log.warn("任务等待资源超时：taskId={}, tier={}", message.getTaskId(), message.getTier());
            boolean vip = "VIP".equalsIgnoreCase(message.getTier());
            taskStatusProducer.send(RunnerTaskStatusMessage.builder()
                    .taskId(message.getTaskId())
                    .dispatchId(message.getDispatchId())
                    .status("FAILED")
                    .errorCode(vip ? "VIP_QUEUE_TIMEOUT" : "RESOURCE_BUSY")
                    .errorMessage(vip ? "VIP 队列等待超时，请稍后重试" : "服务器繁忙，请稍后重试")
                    .occurredAtEpochMs(System.currentTimeMillis())
                    .build());
            channel.basicAck(deliveryTag, false);
        } catch (RuntimeException e) {
            // 瞬时 / 可恢复异常（Redis 抖动、线程池满等）：不 ACK，抛出让 retry 拦截器重试；
            // 重试耗尽后由 defaultRequeueRejected=false 送死信队列（x-dead-letter-exchange），
            // 消息不丢失也不无限重试。
            log.error("createTask 消费失败，等待重试：taskId={}, taskType={}", message.getTaskId(), message.getTaskType(), e);
            throw e;
        }
    }
}
