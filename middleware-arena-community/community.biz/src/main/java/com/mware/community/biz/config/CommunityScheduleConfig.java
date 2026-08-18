package com.mware.community.biz.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 开启社区异步调度任务。
 * <p>
 * 点赞最终链路由 LikeStreamRelay 定时把 Redis Stream Outbox 投递到 RabbitMQ；
 * 旧 MySQL OutboxRelay 仅在 community.outbox.enabled=true 时启用。
 */
@Configuration
@EnableScheduling
public class CommunityScheduleConfig {
}
