package com.mware.runner.biz.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Runner 运行配置（ma.runner.*），覆盖 Docker 接入、平台资源上限、tier 额度、
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

    /** 各 tier 单实验额度 */
    private Tiers tiers = new Tiers();

    /** 公共实验中间件 / 工具镜像 */
    private Images images = new Images();

    /** k6 压测档位 */
    private K6 k6 = new K6();

    /** 用户 SUT 的构建环境 */
    private Build build = new Build();

    /** 进度回传（TODO：接入 MQ / Feign 后启用） */
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
        /** 平台全局并发实验上限（槽位） */
        private int maxConcurrent = 3;
        /** 平台全局 CPU 上限（核） */
        private double maxCpus = 4.0;
        /** 平台全局内存上限（MB） */
        private long maxMemoryMb = 8192;
        /** acquire 等待资源的最长时间（ms），超时抛 ResourceBusyException */
        private long acquireTimeoutMs = 30000;
    }

    @Data
    public static class Tiers {
        private Tier free = new Tier();
        private Tier vip = new Tier();

        @Data
        public static class Tier {
            /** 单实验最多 CPU（核） */
            private double cpus = 1.0;
            /** 单实验最多内存（MB） */
            private long memoryMb = 1024;
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
    }

    @Data
    public static class Progress {
        /** 是否回传阶段进度（false=仅打日志的占位实现） */
        private boolean enabled = false;
        /** 回传通道：mq / feign */
        private String target = "mq";
    }
}
