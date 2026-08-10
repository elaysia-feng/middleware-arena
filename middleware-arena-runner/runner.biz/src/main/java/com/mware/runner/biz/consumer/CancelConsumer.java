package com.mware.runner.biz.consumer;

import com.mware.runner.biz.config.InstanceInfo;
import com.mware.runner.biz.service.RunnerService;
import com.mware.runner.dto.RunnerTaskMessage;

import com.rabbitmq.client.Channel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Runner 定向取消消费者：只消费投递给本实例的取消消息（队列 {@code runner.cancel.{instanceId}}）。
 * <p>
 * experiment-service 发起取消时，先查 Redis 任务→实例登记（{@code runner:task:instance:{taskId}}），
 * 命中则以 instanceId 为 routingKey 定向投递到本队列；本消费者拿到 taskId 后：
 * <ol>
 *   <li>调用 {@link RunnerService#cancelTask(Long)}：取消本地 Future + 清理 Redis 任务实例登记
 *       （业务逻辑收敛到 service 层，consumer 不直接操作登记表 / Redis）；</li>
 *   <li>{@code basicAck} 手动确认。</li>
 * </ol>
 * 消费可靠性（与 {@link TaskConsumer} 同款）：成功 → basicAck；异常 → 不 ACK、向上抛，
 * 由监听容器工厂处理（重试 / 拒绝），避免误确认吞掉取消指令。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CancelConsumer {

    private final RunnerService runnerService;
    private final InstanceInfo instanceInfo;

    @RabbitListener(queues = "#{cancelQueue.name}", ackMode = "MANUAL")
    public void onCancel(RunnerTaskMessage message, Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        Long taskId = message.getTaskId();
        log.info("runner[{}] 收到定向取消：taskId={}", instanceInfo.getInstanceId(), taskId);

        try {
            if (taskId == null) {
                log.warn("runner[{}] 取消消息缺少 taskId，ACK 跳过", instanceInfo.getInstanceId());
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 取消本地登记的任务 + 幂等清理 Redis 任务实例登记，
            // 统一收敛到 RunnerService.cancelTask（consumer 不直接操作登记表 / Redis）
            boolean cancelled = runnerService.cancelTask(taskId);
            log.info("runner[{}] 取消任务：taskId={}, cancelled={}", instanceInfo.getInstanceId(), taskId, cancelled);

            // 手动确认
            channel.basicAck(deliveryTag, false);
        } catch (RuntimeException e) {
            log.error("runner[{}] 定向取消消费失败：taskId={}", instanceInfo.getInstanceId(), taskId, e);
            throw e;
        }
    }
}
