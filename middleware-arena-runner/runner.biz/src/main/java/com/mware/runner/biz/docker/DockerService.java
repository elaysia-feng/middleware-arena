package com.mware.runner.biz.docker;

import com.mware.runner.biz.config.ExperimentType;
import com.mware.runner.biz.config.RunnerProperties;

import java.util.List;

/**
 * docker CLI 薄封装（ProcessBuilder）。
 * <p>
 * 选型说明：本机离线 Maven 仓库（MVN_repo）没有 docker-java-core，SDK 无法离线编译；
 * docker CLI 由 Runner 镜像自带，DOCKER_HOST 指向宿主 socket（docker.sock 挂载），
 * 进程级调用即可覆盖"建网络 / 起容器 / 等健康 / 跑 k6 / 清理"全流程。
 * <p>
 * 命名契约：每个实验一个独立网络 + 前缀命名的容器，容器名天然不冲突，
 * 因此不需要宿主端口池（实验网络内容器名直连，见 {@link ExperimentType}）。
 */
public interface DockerService {

    // ==================== 命名契约 ====================

    /** 实验网络名：{prefix}-{taskId}-net，例如 ma-task-1001-net */
    String networkName(Long taskId);

    /** 实验容器名：{prefix}-{taskId}-{role}，例如 ma-task-1001-redis */
    String containerName(Long taskId, String role);

    /** candidate SUT 镜像名：ma-task-{taskId}-sut:latest（build 产物；run 按同一规则取用） */
    String sutImageName(Long taskId);

    // ==================== 网络 ====================

    void createNetwork(Long taskId);

    void removeNetwork(Long taskId);

    // ==================== 容器 ====================

    /**
     * detached 启动临时实验容器，挂到实验网络。
     *
     * @param role      角色（product / order / redis / mysql ...），决定容器名
     * @param image     镜像
     * @param extraArgs 追加参数（如 --memory / --cpus 硬限制，见 {@link #resourceArgs}）
     */
    void startContainer(Long taskId, String role, String image, List<String> extraArgs);

    void startContainer(Long taskId, String role, String image);

    /** 强制删除容器（幂等语义由调用方按"已清理"容忍不存在） */
    void stopAndRemove(String name);

    /** 由 tier 额度生成 Docker 硬限制参数：--memory / --cpus（最后一道防线，真正决策在 ResourceScheduler） */
    List<String> resourceArgs(RunnerProperties.Tiers.Tier tier);

    // ==================== 镜像 ====================

    void pullImage(String image);

    void buildImage(String tag, String contextDir);

    void removeImage(String tag);

    // ==================== 健康 / 统计 ====================

    /**
     * 轮询 SUT 健康：在实验网络内起一次性 curl 容器（--rm 自删）探测 {url}，成功即返回。
     * 不依赖 SUT 镜像自带 curl。
     * TODO[Runner]：探测失败原因记录 / 超时收敛；探测容器复用 k6 镜像省一次拉取
     */
    boolean waitHealthy(Long taskId, String url, long timeoutSeconds);

    /** docker stats 单容器快照（原始文本，解析见 MetricsCollector） */
    String stats(String containerName);
}
