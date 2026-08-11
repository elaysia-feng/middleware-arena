package com.mware.runner.biz.metrics.impl;

import com.mware.runner.biz.config.ExperimentType;
import com.mware.runner.biz.docker.DockerService;
import com.mware.runner.biz.metrics.MetricsCollector;
import com.mware.runner.dto.RunnerTaskMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 指标采集实现：解析 k6 summary.json + docker stats，组装 metricsJson 回传 experiment。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MetricsCollectorImpl implements MetricsCollector {

    private final DockerService dockerService;

    @Override
    public String collect(RunnerTaskMessage message, ExperimentType type) {
        Long taskId = message.getTaskId();
        log.info("采集指标 taskId={}, type={}", taskId, type);
        return null;
    }
}
