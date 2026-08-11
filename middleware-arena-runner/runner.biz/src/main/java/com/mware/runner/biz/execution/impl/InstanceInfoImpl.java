package com.mware.runner.biz.execution.impl;

import com.mware.runner.biz.execution.InstanceInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * 实例标识实现：构造时按 配置 &gt; HOSTNAME &gt; UUID 顺序确定 instanceId，进程生命周期内不变。
 */
@Component
public class InstanceInfoImpl implements InstanceInfo {

    private final String instanceId;

    public InstanceInfoImpl(@Value("${ma.runner.instance-id:#{null}}") String configuredInstanceId) {
        String hostname = System.getenv("HOSTNAME");

        if (StringUtils.hasText(configuredInstanceId)) {
            this.instanceId = configuredInstanceId;
        } else if (StringUtils.hasText(hostname)) {
            this.instanceId = hostname;
        } else {
            this.instanceId = UUID.randomUUID().toString();
        }
    }

    @Override
    public String getInstanceId() {
        return instanceId;
    }
}
