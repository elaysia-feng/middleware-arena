package com.mware.runner.biz.docker;

import com.mware.runner.biz.config.RunnerProperties;
import com.mware.runner.biz.scheduler.ResourceTier;
import com.mware.runner.dto.RunnerTaskMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个实验的容器环境编排：建独立网络 → 按实验类型起中间件 + SUT → 供 k6 压测 → 清理。
 * <p>
 * 每个实验一个独立 docker network（task-{taskId}-net），容器名互不冲突，
 * k6 通过容器名直连 SUT（如 http://ma-task-1001-product:8080），
 * 不需要宿主端口池 / 端口抢占（对齐方案第 6 点）。
 * <p>
 * TODO[Runner]：SEATA 类型拆多 SUT 角色（order + storage + account，见 ExperimentType）；
 * 实验数据重置（Redis FLUSHDB / 清 MQ 队列 / 重建 ES index）在 baseline→candidate 串行切换时执行。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExperimentEnvironment {

    private final RunnerProperties properties;
    private final DockerService dockerService;

    /**
     * 启动实验环境：创建独立网络 + 按实验类型启动中间件与 SUT 容器。
     *
     * @param sutImage candidate 构建产物 / baseline 预构建镜像名
     * @return SUT 的 k6 压测基址（容器名直连）
     */
    public String start(RunnerTaskMessage message, ExperimentType type, String sutImage) {
        Long taskId = message.getTaskId();
        RunnerProperties.Tiers.Tier tier = tierConfig(message);

        dockerService.createNetwork(taskId);
        for (var entry : type.middlewareImages().entrySet()) {
            String role = entry.getKey();
            String image = resolveImage(entry.getValue());
            dockerService.startContainer(taskId, role, image, dockerService.resourceArgs(tier));
            log.info("实验中间件已启动 taskId={}, role={}, image={}", taskId, role, image);
        }
        dockerService.startContainer(taskId, type.sutRole(), sutImage, dockerService.resourceArgs(tier));

        String baseUrl = "http://" + dockerService.containerName(taskId, type.sutRole())
                + ":" + ExperimentType.SUT_PORT;
        log.info("实验环境已就绪 taskId={}, network={}, sutUrl={}",
                taskId, dockerService.networkName(taskId), baseUrl);
        return baseUrl;
    }

    /** 清理：删除全部实验容器（SUT + 中间件）+ 移除实验网络（容忍容器/网络已不存在）。 */
    public void teardown(RunnerTaskMessage message, ExperimentType type) {
        Long taskId = message.getTaskId();
        List<String> roles = new ArrayList<>(type.middlewareImages().keySet());
        roles.add(type.sutRole());
        for (String role : roles) {
            try {
                dockerService.stopAndRemove(dockerService.containerName(taskId, role));
            } catch (RuntimeException e) {
                log.warn("清理容器失败（忽略，可能已不存在）taskId={}, role={}: {}", taskId, role, e.getMessage());
            }
        }
        try {
            dockerService.removeNetwork(taskId);
        } catch (RuntimeException e) {
            log.warn("清理网络失败（忽略）taskId={}: {}", taskId, e.getMessage());
        }
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
