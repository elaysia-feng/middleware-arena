package com.mware.runner.biz.progress;

import com.mware.runner.biz.config.RunnerRabbitConfig;
import com.mware.runner.dto.RunnerTaskStatusMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/** 回传任务终态；发送失败时抛异常，让原任务消息进入重试。 */
@Component
@RequiredArgsConstructor
public class RunnerTaskStatusProducer {

    private final RabbitTemplate rabbitTemplate;

    public void send(RunnerTaskStatusMessage message) {
        rabbitTemplate.convertAndSend(
                RunnerRabbitConfig.EXCHANGE_STATUS,
                RunnerRabbitConfig.ROUTING_KEY_STATUS,
                message);
    }
}
