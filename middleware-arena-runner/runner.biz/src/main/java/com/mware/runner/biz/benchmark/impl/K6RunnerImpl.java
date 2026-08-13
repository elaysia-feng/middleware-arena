package com.mware.runner.biz.benchmark.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mware.runner.biz.benchmark.K6Runner;
import com.mware.runner.biz.config.ExperimentType;
import com.mware.runner.biz.config.ResourceTier;
import com.mware.runner.biz.config.RunnerProperties;
import com.mware.runner.biz.docker.DockerService;
import com.mware.runner.dto.RunnerTaskMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * k6 压测实现：生成阶梯脚本 → 一次性容器压测 → summary.json 落盘供 collectMetrics 解析。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class K6RunnerImpl implements K6Runner {

    private static final Pattern DURATION_PATTERN = Pattern.compile("^(\\d+)(s|m)$");
    private static final long STOP_SECONDS = 5;
    private static final long CONTAINER_GRACE_SECONDS = 30;

    private final RunnerProperties properties;
    private final DockerService dockerService;
    private final ObjectMapper objectMapper;

    @Override
    public void run(RunnerTaskMessage message, ExperimentType type, String baseUrl) {
        if (message == null || message.getTaskId() == null) {
            throw new IllegalArgumentException("k6 压测缺少 taskId");
        }
        if (type == null || type == ExperimentType.UNKNOWN) {
            throw new IllegalArgumentException("k6 压测缺少有效实验类型");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("k6 压测缺少 SUT 地址");
        }
        Long taskId = message.getTaskId();
        log.info("开始压测 taskId={}, type={}, target={}{} ({})",
                taskId, type, baseUrl, type.k6Path(), type.httpMethod());

        // 1. 解析并限制客户端参数：FREE 最大 20 VU，VIP 最大 200 VU。
        BenchmarkPlan plan = benchmarkPlan(message);

        // 2. 生成任务专属 JS；旧 summary 先删除，避免失败任务读取到上次结果。
        Path workDir = Path.of(properties.getK6().getWorkDir()).toAbsolutePath().normalize();
        String scriptName = "benchmark-" + taskId + ".js";
        String summaryName = "summary-" + taskId + ".json";
        try {
            Files.createDirectories(workDir);
            Files.deleteIfExists(workDir.resolve(summaryName));
            Files.writeString(workDir.resolve(scriptName), buildScript(taskId, type, baseUrl, plan),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("生成 k6 脚本失败，taskId=" + taskId, e);
        }

        // 3. 在实验网络内运行一次性 k6 容器，超时包含所有阶段并额外留 30 秒退出时间。
        RunnerProperties.WorkloadResource workload = properties.getWorkload();
        long timeoutSeconds = plan.totalSeconds() + CONTAINER_GRACE_SECONDS;
        dockerService.runK6(taskId, workDir.toString(), scriptName,
                workload.getK6Cpus(), workload.getK6MemoryMb(), timeoutSeconds);

        // 4. summary 是后续指标采集的必要输入；容器正常退出但没有文件也视为失败。
        if (!Files.isRegularFile(workDir.resolve(summaryName))) {
            throw new RuntimeException("k6 未生成 summary，taskId=" + taskId);
        }
        log.info("压测完成 taskId={}, summary={}", taskId, workDir.resolve(summaryName));
    }

    String buildScript(Long taskId, ExperimentType type, String baseUrl, BenchmarkPlan plan) {
        String targetUrl = baseUrl + type.k6Path();
        String requestBody = plan.requestBody() == null ? "null" : plan.requestBody().toString();
        String headersJson;
        try {
            headersJson = objectMapper.writeValueAsString(plan.headers());
        } catch (IOException e) {
            throw new RuntimeException("序列化 k6 请求头失败，taskId=" + taskId, e);
        }

        return """
                import http from 'k6/http';
                import { check, sleep } from 'k6';

                const targetUrl = %s;
                const requestBody = %s;
                const requestParams = { headers: %s };

                export const options = {
                  stages: [
                    { duration: '%ss', target: %s },
                    { duration: '%ss', target: %s },
                    { duration: '%ss', target: %s },
                    { duration: '%ss', target: 0 },
                  ],
                  thresholds: {
                    http_req_failed: ['rate<0.05'],
                    http_req_duration: ['p(95)<1000'],
                  },
                };

                export default function () {
                  const body = requestBody === null ? null : JSON.stringify(requestBody);
                  const response = http.request(%s, targetUrl, body, requestParams);
                  check(response, {
                    'status is 2xx or 3xx': (result) => result.status >= 200 && result.status < 400,
                  });
                  sleep(0.1);
                }

                export function handleSummary(data) {
                  return {
                    '/scripts/summary-%s.json': JSON.stringify(data),
                  };
                }
                """.formatted(
                jsonString(targetUrl), requestBody, headersJson,
                plan.smokeSeconds(), plan.smokeVus(),
                plan.warmupSeconds(), plan.warmupVus(),
                plan.formalSeconds(), plan.formalVus(),
                STOP_SECONDS,
                jsonString(type.httpMethod()), taskId);
    }

    BenchmarkPlan benchmarkPlan(RunnerTaskMessage message) {
        RunnerProperties.K6 config = properties.getK6();
        int maxVus = ResourceTier.from(message.getTier()) == ResourceTier.VIP ? 200 : 20;
        int smokeVus = Math.min(config.getSmokeVus(), maxVus);
        int warmupVus = Math.min(config.getWarmupVus(), maxVus);
        int formalVus = Math.min(config.getFormalVus(), maxVus);
        long formalSeconds = config.getFormalSeconds();
        JsonNode requestBody = null;
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");

        if (message.getRunParamsJson() != null && !message.getRunParamsJson().isBlank()) {
            try {
                JsonNode params = objectMapper.readTree(message.getRunParamsJson());
                List<Integer> ladder = new ArrayList<>();
                JsonNode ladderNode = params.path("concurrencyLadder");
                if (ladderNode.isArray()) {
                    ladderNode.forEach(node -> {
                        if (node.canConvertToInt() && node.asInt() > 0) {
                            ladder.add(Math.min(node.asInt(), maxVus));
                        }
                    });
                }
                if (!ladder.isEmpty()) {
                    smokeVus = ladder.getFirst();
                    warmupVus = ladder.size() > 1 ? ladder.get(1) : smokeVus;
                    formalVus = ladder.getLast();
                }
                formalSeconds = parseDurationSeconds(params.path("duration").asText(null),
                        config.getFormalSeconds());
                requestBody = params.get("requestBody");
                JsonNode headersNode = params.path("headers");
                if (headersNode.isObject()) {
                    headersNode.fields().forEachRemaining(entry -> headers.put(entry.getKey(), entry.getValue().asText()));
                }
            } catch (IOException e) {
                throw new IllegalArgumentException("runParamsJson 格式错误，taskId=" + message.getTaskId(), e);
            }
        }

        return new BenchmarkPlan(smokeVus, config.getSmokeSeconds(),
                warmupVus, config.getWarmupSeconds(), formalVus, formalSeconds,
                requestBody, Map.copyOf(headers));
    }

    private long parseDurationSeconds(String duration, long defaultSeconds) {
        if (duration == null || duration.isBlank()) {
            return defaultSeconds;
        }
        Matcher matcher = DURATION_PATTERN.matcher(duration.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("duration 只支持秒或分钟，例如 30s、2m");
        }
        long value = Long.parseLong(matcher.group(1));
        long seconds = "m".equals(matcher.group(2)) ? Math.multiplyExact(value, 60) : value;
        if (seconds < 1 || seconds > 600) {
            throw new IllegalArgumentException("duration 必须在 1 秒到 10 分钟之间");
        }
        return seconds;
    }

    private String jsonString(String value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException e) {
            throw new RuntimeException("生成 k6 字符串失败", e);
        }
    }

    record BenchmarkPlan(int smokeVus, long smokeSeconds,
            int warmupVus, long warmupSeconds,
            int formalVus, long formalSeconds,
            JsonNode requestBody, Map<String, String> headers) {

        long totalSeconds() {
            return smokeSeconds + warmupSeconds + formalSeconds + STOP_SECONDS;
        }
    }
}
