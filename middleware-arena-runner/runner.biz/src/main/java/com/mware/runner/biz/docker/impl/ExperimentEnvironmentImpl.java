package com.mware.runner.biz.docker.impl;

import com.mware.runner.biz.config.ExperimentType;
import com.mware.runner.biz.config.RunnerProperties;
import com.mware.runner.biz.docker.DockerService;
import com.mware.runner.biz.docker.ExperimentEnvironment;
import com.mware.runner.dto.RunnerTaskMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 实验环境编排实现：start 建网起容器返回 SUT 基址，teardown 幂等清理。
 * <p>
 * 多 SUT 实验（SEATA = order + storage + account）按 {@code type.sutSpecs()}
 * 逐个启动候选镜像容器，k6 只压主链路入口（{@code type.sutRole()}）。
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
            if (properties.getSharedServices().isEnabled()
                    && ("mysql".equals(role) || "redis".equals(role))) {
                log.info("复用宿主机基础设施，跳过任务容器 taskId={}, role={}", taskId, role);
                continue;
            }
            String image = resolveImage(spec.imageConfigKey());
            dockerService.startContainer(taskId, role, image,
                    dockerService.resourceArgs(spec.cpus(), spec.memoryMb()));
        }

        // 3. 全部 SUT（多 SUT 实验 = order + storage + account）都使用当前实验定义的资源，
        //    而不是按会员等级固定分配；k6 基址固定指向主链路入口容器。
        for (ExperimentType.SutSpec spec : type.sutSpecs()) {
            List<String> args = new ArrayList<>(
                    dockerService.resourceArgs(spec.cpus(), spec.memoryMb()));
            if (properties.getSharedServices().isEnabled()) {
                RunnerProperties.SharedServices shared = properties.getSharedServices();
                args.addAll(List.of(
                        // Runner 的实验网络统一通过 SUT_PORT=8080 探测；覆盖模板宿主服务原本的 9006。
                        "--env", "SERVER_PORT=" + ExperimentType.SUT_PORT,
                        "--env", "MYSQL_ADDR=" + shared.getMysqlAddr(),
                        "--env", "MYSQL_DATABASE=" + shared.getMysqlDatabase(),
                        "--env", "REDIS_HOST=" + shared.getRedisHost(),
                        "--env", "REDIS_PORT=" + shared.getRedisPort()));
            }
            dockerService.startContainer(taskId, spec.role(), sutImage, args);
        }

        return "http://" + dockerService.containerName(taskId, type.sutRole())
                + ":" + ExperimentType.SUT_PORT;
    }

    @Override
    public void teardown(RunnerTaskMessage message, ExperimentType type) {
        Long taskId = message.getTaskId();

        // 1. 删除当前任务的全部 SUT 容器（多 SUT 实验 = order + storage + account）
        for (ExperimentType.SutSpec spec : type.sutSpecs()) {
            dockerService.stopAndRemove(
                    dockerService.containerName(taskId, spec.role()));
        }

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
