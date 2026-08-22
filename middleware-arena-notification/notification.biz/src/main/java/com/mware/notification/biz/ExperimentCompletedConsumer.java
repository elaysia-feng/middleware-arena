package com.mware.notification.biz;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** 将实验完成事件转换为幂等站内通知。 */
@Component
public class ExperimentCompletedConsumer {
    private final NotificationService notificationService;

    public ExperimentCompletedConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = NotificationRabbitConfig.QUEUE)
    public void consume(String message) {
        notificationService.handleExperimentCompleted(message);
    }
}
