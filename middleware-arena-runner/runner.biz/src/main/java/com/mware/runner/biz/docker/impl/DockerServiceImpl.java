package com.mware.runner.biz.docker.impl;

import com.mware.runner.biz.config.RunnerProperties;
import com.mware.runner.biz.docker.DockerService;
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
 * docker CLI 薄封装实现（ProcessBuilder）：DOCKER_HOST + 输出落临时文件防管道死锁 + 非零退出抛异常。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DockerServiceImpl implements DockerService {

    // 心跳
    private static final long HEALTH_POLL_MS = 1000;

    private final RunnerProperties properties;

    // ==================== 命名契约 ====================

    @Override
    public String networkName(Long taskId) {
        return properties.getDocker().getContainerPrefix() + "-" + taskId + "-net";
    }

    @Override
    public String containerName(Long taskId, String role) {
        return properties.getDocker().getContainerPrefix() + "-" + taskId + "-" + role;
    }

    @Override
    public String sutImageName(Long taskId) {
        return properties.getDocker().getContainerPrefix() + "-" + taskId + "-sut:latest";
    }

    // ==================== 网络 ====================

    @Override
    public void createNetwork(Long taskId) {
        run(List.of("network", "create", "--driver", "bridge", networkName(taskId)));
    }

    @Override
    public void removeNetwork(Long taskId) {
        // docker network rm {networkName}：网络不存在时容忍（幂等清理语义）
        try {
            run(List.of("network", "rm", networkName(taskId)));
        } catch (RuntimeException e) {
            if (e.getMessage().contains("no such network")) {
                log.debug("网络已不存在，忽略: {}", networkName(taskId));
            } else {
                throw e; // 真正的错误还是要抛
            }
        }
    }
    // ==================== 容器 ====================

    @Override
    public void startContainer(Long taskId, String role, String image, List<String> extraArgs) {
        // TODO[Runner]：docker run -d --name {containerName} --network {networkName}
        // {extraArgs} {image}，见 run()
    }

    @Override
    public void startContainer(Long taskId, String role, String image) {
        startContainer(taskId, role, image, List.of());
    }

    @Override
    public void stopAndRemove(String name) {
        // docker rm -f {name}：容器不存在时容忍（幂等清理语义）
        try {
            run(List.of("rm", "-f", name));
        } catch (RuntimeException e) {
            if (e.getMessage().contains("No such container")) {
                log.debug("容器已不存在，忽略: {}", name);
            } else {
                throw e; // 真正的错误还是要抛
            }
        }
    }

    @Override
    public List<String> resourceArgs(RunnerProperties.Tiers.Tier tier) {
        // 由 tier 额度生成 Docker 硬限制参数：--memory {memoryMb}m --cpus {cpus}
        // （最后一道防线，真正决策在 ResourceScheduler）
        return List.of(
                "--memory", tier.getMemoryMb() + "m",
                "--cpus", String.valueOf(tier.getCpus()));
    }

    // ==================== 镜像 ====================

    @Override
    public void pullImage(String image) {
        // docker pull {image}，见 run()
        run(List.of("pull", image));
    }

    @Override
    public void buildImage(String tag, String contextDir) {
        // docker build -t {tag} {contextDir}，见 run()
        run(List.of("build", "-t", tag, contextDir));

    }

    @Override
    public void removeImage(String tag) {
        // docker rmi -f {tag}：镜像不存在时容忍（幂等清理语义）
        try {
            run(List.of("rmi", "-f", tag));
        } catch (RuntimeException e) {
            if (e.getMessage().contains("No such image")) {
                log.debug("镜像已不存在，忽略: {}", tag);
            } else {
                throw e; // 真正的错误还是要抛
            }
        }
    }

    // ==================== 健康 / 统计 ====================

    @Override
    public boolean waitHealthy(Long taskId, String url, long timeoutSeconds) {
        // 实验网络内起一次性 curl 容器（--rm 自删）轮询 {url}，HEALTH_POLL_MS 间隔、
        // timeoutSeconds 超时；不依赖 SUT 镜像自带 curl（SUT 精简镜像未必装了 curl）。
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            try {
                // docker run --rm --network {net} {probe} -s -o /dev/null -w "%{http_code}" --max-time 2 {url}
                // --max-time 2：SUT 未就绪时探测容器最多 2 秒返回，保证轮询节奏可控
                String code = run(List.of(
                        "run", "--rm",
                        "--network", networkName(taskId),
                        properties.getImages().getProbe(),
                        "-s", "-o", "/dev/null", "-w", "%{http_code}",
                        "--max-time", "2",
                        url)).trim();
                // 2xx/3xx 视为健康；连接拒绝 / 非 2xx 都算还没好，继续等下轮
                if (code.startsWith("2") || code.startsWith("3")) {
                    return true;
                }
            } catch (RuntimeException e) {
                // SUT 未就绪（连接拒绝、容器未起）→ 忽略，继续轮询
                log.debug("健康探测未通过，继续轮询: {}", e.getMessage());
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

    @Override
    public String stats(String containerName) {
        // docker stats --no-stream --format "{{.CPUPerc}}|{{.MemUsage}}" {containerName}，
        // --no-stream 只取当前快照（否则持续流式刷新，run() 会卡到超时）；
        // 返回形如 "0.50%|12.3MiB / 128MiB"，下游 split("\\|") 解析 CPU/内存。
        return run(List.of("stats", "--no-stream", "--format", "{{.CPUPerc}}|{{.MemUsage}}", containerName)).trim();
    }

    // ==================== 底层执行 ====================

    /** 执行 docker CLI，输出落临时文件再读（避免管道 64KB 写满导致 waitFor 死锁）。 */
    private String run(List<String> args) {
        try {
            // 1. 拼接指令：binary 放最前（docker CLI 可执行文件），后面跟调用方传入的参数
            List<String> command = new ArrayList<>(args);
            command.add(0, properties.getDocker().getBinary());
            ProcessBuilder pb = new ProcessBuilder(command);

            // 2. 指定连接哪个 docker daemon
            pb.environment().put("DOCKER_HOST", properties.getDocker().getHost());

            // 3. 输出重定向到临时文件（防 64KB 管道写满死锁）——必须在 start() 之前设置
            File out = File.createTempFile("docker-", ".out");
            pb.redirectOutput(out);   // stdout → 文件
            pb.redirectError(out);    // stderr → 同一文件

            // 4. 启动进程
            Process p = pb.start();

            // 5. 等待结束，超时 destroyForcibly（用 commandTimeoutSeconds，非 HEALTH_POLL_MS）
            if (!p.waitFor(properties.getDocker().getCommandTimeoutSeconds(), TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new RuntimeException("docker 命令超时：" + args);
            }

            // 6. 读回输出，非零退出码抛异常带输出文本
            String output = Files.readString(out.toPath());
            if (p.exitValue() != 0) {
                throw new RuntimeException("docker 命令失败(" + p.exitValue() + "): " + output + " args=" + args);
            }
            return output;
        } catch (IOException | InterruptedException e) {
            // 受检异常统一转运行时异常：调用方（createNetwork 等）无需逐个声明 throws
            throw new RuntimeException("docker 命令执行失败 args=" + args, e);
        }
    }
}
