package com.mware.runner.biz.metrics.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mware.runner.biz.config.ExperimentType;
import com.mware.runner.biz.config.RunnerProperties;
import com.mware.runner.biz.docker.DockerService;
import com.mware.runner.biz.metrics.MetricsCollector;
import com.mware.runner.dto.RunnerTaskMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 指标采集实现：解析 k6 summary.json + docker stats，组装 metricsJson 回传 experiment。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MetricsCollectorImpl implements MetricsCollector {

    private final RunnerProperties properties;
    private final DockerService dockerService;
    private final ObjectMapper objectMapper;

    @Override
    public String collect(RunnerTaskMessage message, ExperimentType type) {
        Long taskId = message.getTaskId();
        log.info("采集指标 taskId={}, type={}", taskId, type);

        // 1. 读取 k6 summary，提取压测期间统计出来的核心业务指标。
        Path summaryPath = Path.of(properties.getK6().getWorkDir(), "summary-" + taskId + ".json");
        JsonNode metrics;
        try {
            metrics = objectMapper.readTree(summaryPath.toFile()).path("metrics");
        } catch (IOException e) {
            throw new RuntimeException("读取 k6 summary 失败，taskId=" + taskId, e);
        }

        double qps = metrics.path("http_reqs").path("values").path("rate").asDouble();
        long p95Ms = Math.round(metrics.path("http_req_duration").path("values").path("p(95)").asDouble());
        double errorRate = metrics.path("http_req_failed").path("values").path("rate").asDouble();
        long concurrency = Math.round(metrics.path("vus_max").path("values").path("max").asDouble());

        // 2. 采集 SUT 和中间件的结束时资源快照，汇总 CPU 与内存。
        double totalCpuPercent = 0;
        double totalMemoryMb = 0;
        List<String> roles = new ArrayList<>(type.middlewareImages().keySet());
        roles.add(type.sutRole());
        for (String role : roles) {
            String containerName = dockerService.containerName(taskId, role);
            String rawStats = dockerService.stats(containerName);
            String[] parts = rawStats.split("\\|", 2);
            totalCpuPercent += Double.parseDouble(parts[0].replace("%", "").trim());
            totalMemoryMb += memoryMb(parts[1].split("/", 2)[0].trim());
        }

        // 3. 返回结构化 JSON，供 experiment-service 保存实验结果。
        ObjectNode result = objectMapper.createObjectNode();
        result.put("qps", qps);
        result.put("p95Ms", p95Ms);
        result.put("errorRate", errorRate);
        result.put("concurrency", concurrency);
        result.put("cpu", totalCpuPercent / 100.0);
        result.put("memMb", Math.round(totalMemoryMb));
        log.info("指标采集完成 taskId={}, qps={}, p95Ms={}, errorRate={}",
                taskId, qps, p95Ms, errorRate);
        return result.toString();
    }

    /** 把 docker stats 的内存值统一换算成 MiB。 */
    private double memoryMb(String value) {
        if (value.endsWith("GiB")) {
            return Double.parseDouble(value.replace("GiB", "")) * 1024;
        }
        if (value.endsWith("MiB")) {
            return Double.parseDouble(value.replace("MiB", ""));
        }
        if (value.endsWith("KiB")) {
            return Double.parseDouble(value.replace("KiB", "")) / 1024;
        }
        return Double.parseDouble(value.replace("B", "")) / 1024 / 1024;
    }
}
