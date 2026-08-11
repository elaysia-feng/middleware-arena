package com.mware.runner.biz.docker;

import com.mware.runner.biz.config.RunnerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * docker CLI 薄封装（ProcessBuilder）。
 * <p>
 * 选型说明：本机离线 Maven 仓库（MVN_repo）没有 docker-java-core，SDK 无法离线编译；
 * docker CLI 由 Runner 镜像自带，DOCKER_HOST 指向宿主 socket（docker.sock 挂载），
 * 进程级调用即可覆盖"建网络 / 起容器 / 等健康 / 跑 k6 / 清理"全流程。
 * <p>
 * 所有命令统一走 {@link #run(List)}：拼 DOCKER_HOST、输出落临时文件（防管道写满死锁）、
 * 非零退出抛异常（带输出文本）。
 * <p>
 * 命名契约：每个实验一个独立网络 + 前缀命名的容器，容器名天然不冲突，
 * 因此不需要宿主端口池（实验网络内容器名直连，见 {@link ExperimentType}）。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DockerService {

    private static final long HEALTH_POLL_MS = 1000;

    private final RunnerProperties properties;

    // ==================== 命名契约 ====================

    /** 实验网络名：{prefix}-{taskId}-net，例如 ma-task-1001-net */
    public String networkName(Long taskId) {
        return properties.getDocker().getContainerPrefix() + "-" + taskId + "-net";
    }

    /** 实验容器名：{prefix}-{taskId}-{role}，例如 ma-task-1001-redis */
    public String containerName(Long taskId, String role) {
        return properties.getDocker().getContainerPrefix() + "-" + taskId + "-" + role;
    }

    /** candidate SUT 镜像名：ma-task-{taskId}-sut:latest（build 产物；run 按同一规则取用） */
    public String sutImageName(Long taskId) {
        return properties.getDocker().getContainerPrefix() + "-" + taskId + "-sut:latest";
    }

    // ==================== 网络 ====================

    public void createNetwork(Long taskId) {
        run(List.of("network", "create", "--driver", "bridge", networkName(taskId)));
    }

    public void removeNetwork(Long taskId) {
        run(List.of("network", "rm", networkName(taskId)));
    }

    // ==================== 容器 ====================

    /**
     * detached 启动临时实验容器，挂到实验网络。
     *
     * @param role     角色（product / order / redis / mysql ...），决定容器名
     * @param image    镜像
     * @param extraArgs 追加参数（如 --memory / --cpus 硬限制，见 {@link #resourceArgs})
     */
    public void startContainer(Long taskId, String role, String image, List<String> extraArgs) {
        List<String> cmd = new ArrayList<>(List.of("run", "-d",
                "--name", containerName(taskId, role),
                "--network", networkName(taskId)));
        cmd.addAll(extraArgs);
        cmd.add(image);
        run(cmd);
    }

    public void startContainer(Long taskId, String role, String image) {
        startContainer(taskId, role, image, List.of());
    }

    /** 强制删除容器（幂等语义由调用方按"已清理"容忍不存在） */
    public void stopAndRemove(String name) {
        run(List.of("rm", "-f", name));
    }

    /** 由 tier 额度生成 Docker 硬限制参数：--memory / --cpus（最后一道防线，真正决策在 ResourceScheduler） */
    public List<String> resourceArgs(RunnerProperties.Tiers.Tier tier) {
        return List.of("--memory=" + tier.getMemoryMb() + "m", "--cpus=" + tier.getCpus());
    }

    // ==================== 镜像 ====================

    public void pullImage(String image) {
        run(List.of("pull", image));
    }

    public void buildImage(String tag, String contextDir) {
        run(List.of("build", "-t", tag, contextDir));
    }

    public void removeImage(String tag) {
        run(List.of("rmi", "-f", tag));
    }

    // ==================== 健康 / 统计 ====================

    /**
     * 轮询 SUT 健康：在实验网络内起一次性 curl 容器（--rm 自删）探测 {url}，成功即返回。
     * 不依赖 SUT 镜像自带 curl。
     * TODO[Runner]：探测失败原因记录 / 超时收敛；探测容器复用 k6 镜像省一次拉取
     */
    public boolean waitHealthy(Long taskId, String url, long timeoutSeconds) {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000;
        while (System.currentTimeMillis() < deadline) {
            try {
                run(List.of("run", "--rm", "--network", networkName(taskId),
                        properties.getImages().getProbe(), "curl", "-sf", url));
                return true;
            } catch (RuntimeException e) {
                // SUT 未就绪：退避后重试
            }
            try {
                Thread.sleep(HEALTH_POLL_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /** docker stats 单容器快照（原始文本，解析见 MetricsCollector） */
    public String stats(String containerName) {
        return run(List.of("stats", "--no-stream",
                "--format", "{{.CPUPerc}}|{{.MemUsage}}", containerName));
    }

    // ==================== 底层执行 ====================

    /** 执行 docker CLI，输出落临时文件再读（避免管道 64KB 写满导致 waitFor 死锁）。 */
    private String run(List<String> args) {
        List<String> cmd = new ArrayList<>();
        cmd.add(properties.getDocker().getBinary());
        cmd.addAll(args);

        File outFile = null;
        try {
            outFile = File.createTempFile("ma-docker-", ".log");
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.environment().put("DOCKER_HOST", properties.getDocker().getHost());
            pb.redirectOutput(outFile);
            pb.redirectErrorStream(true);

            Process p = pb.start();
            boolean done = p.waitFor(properties.getDocker().getCommandTimeoutSeconds(), TimeUnit.SECONDS);
            String out = Files.readString(outFile.toPath(), StandardCharsets.UTF_8).trim();

            if (!done) {
                p.destroyForcibly();
                throw new IllegalStateException("docker 命令超时：" + cmd + "\n" + out);
            }
            if (p.exitValue() != 0) {
                throw new IllegalStateException("docker " + args.get(0) + " 失败 exit="
                        + p.exitValue() + "\n" + out);
            }
            return out;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("docker CLI 调用异常：" + cmd, e);
        } finally {
            if (outFile != null) {
                outFile.delete();
            }
        }
    }
}
