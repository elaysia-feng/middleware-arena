package com.mware.runner.biz.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Runner 运行配置（ma.runner.*），覆盖 Docker 接入、平台资源上限、会员并发限制、
 * 公共镜像、k6 档位与 build 环境。
 * <p>
 * 设计对齐"公共镜像常驻磁盘、实验环境按任务临时启动、baseline/candidate 串行"：
 * 镜像与 k6 档位只在这里配置，业务代码不硬编码镜像 tag。
 */
@Component
@ConfigurationProperties(prefix = "ma.runner")
@Data
public class RunnerProperties {

    /** Docker 接入（Runner 镜像自带 docker CLI，指向宿主 socket） */
    private Docker docker = new Docker();

    /** 平台全局资源上限（防止大量普通用户把机器吃爆） */
    private PlatformResource platform = new PlatformResource();

    /** 各会员等级的调度待遇；实验 CPU/内存需求由 ExperimentType 决定。 */
    private Tiers tiers = new Tiers();

    /** 构建、压测和单任务运行开销。 */
    private WorkloadResource workload = new WorkloadResource();

    /** 公共实验中间件 / 工具镜像 */
    private Images images = new Images();

    /** k6 压测档位 */
    private K6 k6 = new K6();

    /** 用户 SUT 的构建环境 */
    private Build build = new Build();

    /** 任务阶段、成功指标和失败原因的回传配置。 */
    private Progress progress = new Progress();

    @Data
    public static class Docker {
        /** docker CLI 可执行文件（容器内自带，或宿主 PATH） */
        private String binary = "docker";
        /** DOCKER_HOST：容器内挂载 docker.sock；Linux unix:///var/run/docker.sock，Windows 调试可指 npipe */
        private String host = "unix:///var/run/docker.sock";
        /** 单条 CLI 命令超时（秒） */
        private long commandTimeoutSeconds = 120;
        /** 实验网络/容器命名前缀：{prefix}-{taskId}-{role} / {prefix}-{taskId}-net */
        private String containerPrefix = "ma-task";
    }

    @Data
    public static class PlatformResource {
        /** 调度模式：LOCAL=单实例，REDIS=多实例 */
        private SchedulerMode schedulerMode = SchedulerMode.LOCAL;
        /** 平台全局并发实验上限（槽位） */
        private int maxConcurrent = 3;
        /** 平台全局 CPU 上限（核） */
        private double maxCpus = 4.0;
        /** 平台全局内存上限（MB） */
        private long maxMemoryMb = 8192;
        /** 为宿主系统、Docker daemon 和 Runner JVM 固定保留的 CPU。 */
        private double systemReservedCpus = 0.5;
        /** 为宿主系统、Docker daemon 和 Runner JVM 固定保留的内存。 */
        private long systemReservedMemoryMb = 1024;
        /** FREE 从入队开始最多等待 10 秒。 */
        private long freeAcquireTimeoutMs = 10000;
        /** VIP 从入队开始最多等待 5 分钟。 */
        private long vipAcquireTimeoutMs = 300000;
    }

    @Data
    public static class WorkloadResource {
        /** Maven 编译和 docker build 的阶段峰值 CPU。 */
        private double buildCpus = 1.0;
        /** Maven 编译和 docker build 的阶段峰值内存。 */
        private long buildMemoryMb = 1024;
        /** k6 压测容器 CPU。 */
        private double k6Cpus = 0.5;
        /** k6 压测容器内存。 */
        private long k6MemoryMb = 512;
        /** 每个任务在 Runner JVM 中产生的额外 CPU 开销。 */
        private double perTaskCpus = 0.25;
        /** 每个任务的上下文、日志和指标采集内存开销。 */
        private long perTaskMemoryMb = 256;
    }

    public enum SchedulerMode {
        LOCAL,
        REDIS
    }

    @Data
    public static class Tiers {
        private Tier free = new Tier();
        private Tier vip = new Tier();

        @Data
        public static class Tier {
            /** 单个用户同时运行的任务上限。 */
            private int maxConcurrentPerUser = 1;
        }
    }

    @Data
    public static class Images {
        private String redis = "redis:7";
        private String rabbitmq = "rabbitmq:management";
        private String elasticsearch = "docker.elastic.co/elasticsearch/elasticsearch:8.13.4";
        private String seata = "seataio/seata-server:2.1.0";
        private String mysql = "mysql:8.0";
        private String k6 = "grafana/k6:0.47.0";
        /** 健康探测一次性容器镜像（--rm，不依赖 SUT 自带 curl） */
        private String probe = "curlimages/curl:8.5.0";
        /** baseline 预构建镜像名模板：{prefix}{type}{suffix} → ma-redis-baseline:v1 */
        private String baselinePrefix = "ma-";
        private String baselineSuffix = ":v1";
    }

    @Data
    public static class K6 {
        /** k6 脚本 / summary 输出目录（runner 容器内，需与宿主/实验卷共享） */
        private String workDir = "/tmp/ma-k6";
        private int smokeVus = 5;
        private long smokeSeconds = 10;
        private int warmupVus = 10;
        private long warmupSeconds = 20;
        /** 正式压测档位（VIP 再开放 50/100/200 VU、更长 duration） */
        private int formalVus = 20;
        private long formalSeconds = 30;
    }

    @Data
    public static class Build {
        /** 用户 candidate 源码工作目录（宿主卷挂入容器） */
        private String workDir = "/var/lib/ma-runner/work";
        /** SUT 模板工程根目录（TODO：模板实际存储/拉取方式） */
        private String templateDir = "/var/lib/ma-runner/templates";
        private String maven = "/usr/local/maven/bin/mvn";
        private String jdkHome = "/usr/local/jdk";
        /** 离线 Maven 仓库（MVN_repo），对应 CLAUDE.md 离线编译约束 */
        private String localRepo = "/usr/local/maven/repo";
        /** 离线编译开关 */
        private boolean offline = true;
        /** mvn 编译单次超时（秒）；mvn package 可能超过 docker.commandTimeoutSeconds(120s)，单独配置 */
        private long compileTimeoutSeconds = 300;
    }

    @Data
    public static class Progress {
        /** 是否回传阶段进度（false=仅打日志的占位实现） */
        private boolean enabled = false;
        /** 回传通道：mq / feign */
        private String target = "mq";
    }
}
