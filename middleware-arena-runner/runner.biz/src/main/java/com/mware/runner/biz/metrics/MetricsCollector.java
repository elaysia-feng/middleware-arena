package com.mware.runner.biz.metrics;

import com.mware.runner.biz.docker.DockerService;
import com.mware.runner.biz.docker.ExperimentType;
import com.mware.runner.dto.RunnerTaskMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * collectMetrics()：解析 k6 summary.json（吞吐 / 错误率 / P95 延迟 / 并发）
 * + docker stats 系统指标（SUT / 中间件 CPU / 内存），
 * 组装 metricsJson 回传 experiment 持久化到 ExperimentResult。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MetricsCollector {

    private final DockerService dockerService;

    /**
     * 采集一次实验的全部指标，返回结构化 metricsJson。
     *
     * TODO[Runner]：
     * <ol>
     *   <li>读 {@code k6.work-dir}/summary-{taskId}.json → 吞吐 / 错误率 / P95 延迟 / 并发；</li>
     *   <li>{@link DockerService#stats} 采集 SUT + 中间件 CPU / 内存快照；</li>
     *   <li>组装 {@code {"qps":..., "p95Ms":..., "errorRate":..., "cpu":..., "memMb":...}}
     *       回传 experiment（MQ / Feign）落 ExperimentResult（结构化字段 + metricsJson）。</li>
     * </ol>
     */
    public String collect(RunnerTaskMessage message, ExperimentType type) {
        Long taskId = message.getTaskId();
        log.info("采集指标 taskId={}, type={}", taskId, type);
        return null;
    }
}
