package com.mware.community.biz.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 开启 Spring 定时任务。
 * <p>
 * 使用者：OutboxRelay（PENDING → RabbitMQ）、LikeCountFlushScheduler（待刷计数 → MySQL 批量 UPDATE）。
 * 间隔 / 批大小由 application.yml 的 {@code community.outbox.*} 配置。
 */
@Configuration
@EnableScheduling
public class CommunityScheduleConfig {
}
