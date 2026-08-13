package com.mware.runner.biz.docker.impl;

import com.mware.runner.biz.config.ExperimentType;
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
        Long taskId = message.getTaskId();
        // 1. 每个任务使用独立网络，避免不同实验的容器和数据相互影响。
        dockerService.createNetwork(taskId);

        // 2. 只启动当前实验需要的中间件，每个容器使用自己的资源硬限制。
        for (var entry : type.middlewareImages().entrySet()) {
            String role = entry.getKey();
            ExperimentType.ContainerSpec spec = entry.getValue();
            String image = resolveImage(spec.imageConfigKey());
            dockerService.startContainer(taskId, role, image,
                    dockerService.resourceArgs(spec.cpus(), spec.memoryMb()));
        }

        // 3. SUT 也使用当前实验定义的资源，而不是按会员等级固定分配。
        String sutRole = type.sutRole();
        ExperimentType.ContainerResource sutResource = type.sutResource();
        dockerService.startContainer(taskId, sutRole, sutImage,
                dockerService.resourceArgs(sutResource.cpus(), sutResource.memoryMb()));

        return "http://" + dockerService.containerName(taskId, sutRole) + ":" + ExperimentType.SUT_PORT;
    }

    @Override
    public void teardown(RunnerTaskMessage message, ExperimentType type) {
        Long taskId = message.getTaskId();

        // 1. 删除当前任务的 SUT 容器
        dockerService.stopAndRemove(
                dockerService.containerName(taskId, type.sutRole()));

        // 2. 删除当前任务启动的中间件容器
        for (String role : type.middlewareImages().keySet()) {
            dockerService.stopAndRemove(
                    dockerService.containerName(taskId, role));
        }

        // 3. 容器删除后，移除当前任务的独立网络
        dockerService.removeNetwork(taskId);
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
