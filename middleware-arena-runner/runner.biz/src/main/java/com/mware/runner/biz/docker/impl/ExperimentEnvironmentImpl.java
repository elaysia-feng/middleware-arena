package com.mware.runner.biz.docker.impl;

import com.mware.runner.biz.config.ExperimentType;
import com.mware.runner.biz.config.ResourceTier;
import com.mware.runner.biz.config.RunnerProperties;
import com.mware.runner.biz.docker.DockerService;
import com.mware.runner.biz.docker.ExperimentEnvironment;
import com.mware.runner.dto.RunnerTaskMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 实验环境编排实现：start 建网起容器返回 SUT 基址，teardown 幂等清理。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExperimentEnvironmentImpl implements ExperimentEnvironment {

    private final RunnerProperties properties;
    private final DockerService dockerService;

    @Override
    public String start(RunnerTaskMessage message, ExperimentType type, String sutImage) {
        // TODO[Runner]：完整实现——建独立网络 → 按 type.middlewareImages() 逐中间件起容器
        // （带 resourceArgs(tier) 硬限制）→ 起 SUT 容器，返回 http://{sut容器名}:{SUT_PORT} 供 k6 压测。
        return null;
    }

    @Override
    public void teardown(RunnerTaskMessage message, ExperimentType type) {
        // TODO[Runner]：完整实现——删除全部实验容器（SUT + 中间件）+ 移除实验网络，
        // 容忍容器/网络已不存在（幂等清理）。
    }

    private RunnerProperties.Tiers.Tier tierConfig(RunnerTaskMessage message) {
        return switch (ResourceTier.from(message.getTier())) {
            case VIP -> properties.getTiers().getVip();
            case FREE -> properties.getTiers().getFree();
        };
    }

    /** 把 RunnerProperties.images 的字段名解析为实际镜像 tag */
    private String resolveImage(String configKey) {
        return switch (configKey) {
            case "mysql" -> properties.getImages().getMysql();
            case "redis" -> properties.getImages().getRedis();
            case "rabbitmq" -> properties.getImages().getRabbitmq();
            case "elasticsearch" -> properties.getImages().getElasticsearch();
            case "seata" -> properties.getImages().getSeata();
            default -> configKey;
        };
    }
}
