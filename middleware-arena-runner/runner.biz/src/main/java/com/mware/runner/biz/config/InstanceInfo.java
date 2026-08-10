package com.mware.runner.biz.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * 实例标识（instanceId）提供者。
 * <p>
 * 多实例部署时，每个 runner 进程必须持有唯一的实例标识，用于日志链路、
 * {@link RunningTaskManager} 任务归置等场景区分"是谁在处理任务"。
 * <p>
 * 生成策略（按优先级）：
 * <ol>
 *   <li>配置项 {@code ma.runner.instance-id}：人工显式指定（如弹性部署时自定义实例名）；</li>
 *   <li>环境变量 {@code HOSTNAME}：K8s 下即 pod name，天然唯一，上 K8s 后可直接采用；</li>
 *   <li>兜底 {@code UUID.randomUUID()}：本地/单机开发每次启动随机，仅保证进程内唯一。</li>
 * </ol>
 * instanceId 在构造时确定、进程生命周期内不变。
 */
@Component
public class InstanceInfo {

    private final String instanceId;

    public InstanceInfo(@Value("${ma.runner.instance-id:#{null}}") String configuredInstanceId) {
        String hostname = System.getenv("HOSTNAME");

        if (StringUtils.hasText(configuredInstanceId)) {
            this.instanceId = configuredInstanceId;
        } else if (StringUtils.hasText(hostname)) {
            this.instanceId = hostname;
        } else {
            this.instanceId = UUID.randomUUID().toString();
        }
    }

    public String getInstanceId() {
        return instanceId;
    }
}
