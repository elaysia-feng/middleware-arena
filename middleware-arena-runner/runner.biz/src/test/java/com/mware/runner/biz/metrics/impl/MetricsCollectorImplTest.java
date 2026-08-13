package com.mware.runner.biz.metrics.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mware.runner.biz.config.ExperimentType;
import com.mware.runner.biz.config.RunnerProperties;
import com.mware.runner.biz.docker.DockerService;
import com.mware.runner.dto.RunnerTaskMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MetricsCollectorImplTest {

    @TempDir
    Path workDir;

    @Test
    void collectShouldCombineK6AndContainerMetrics() throws Exception {
        Files.writeString(workDir.resolve("summary-1001.json"), """
                {
                  "metrics": {
                    "http_reqs": {"values": {"rate": 123.45}},
                    "http_req_duration": {"values": {"p(95)": 87.6}},
                    "http_req_failed": {"values": {"rate": 0.02}},
                    "vus_max": {"values": {"max": 20}}
                  }
                }
                """);
        Map<String, String> stats = Map.of(
                "ma-task-1001-product", "12.50%|256MiB / 512MiB",
                "ma-task-1001-mysql", "5.00%|1GiB / 2GiB",
                "ma-task-1001-redis", "0.50%|512KiB / 256MiB");
        RunnerProperties properties = new RunnerProperties();
        properties.getK6().setWorkDir(workDir.toString());
        ObjectMapper objectMapper = new ObjectMapper();
        MetricsCollectorImpl collector = new MetricsCollectorImpl(
                properties, dockerService(stats), objectMapper);

        String metricsJson = collector.collect(RunnerTaskMessage.builder().taskId(1001L).build(),
                ExperimentType.REDIS);
        JsonNode result = objectMapper.readTree(metricsJson);

        assertEquals(123.45, result.path("qps").asDouble());
        assertEquals(88, result.path("p95Ms").asLong());
        assertEquals(0.02, result.path("errorRate").asDouble());
        assertEquals(20, result.path("concurrency").asLong());
        assertEquals(0.18, result.path("cpu").asDouble(), 0.0001);
        assertEquals(1281, result.path("memMb").asLong());
    }

    @Test
    void collectShouldRejectMissingSummary() {
        RunnerProperties properties = new RunnerProperties();
        properties.getK6().setWorkDir(workDir.toString());
        MetricsCollectorImpl collector = new MetricsCollectorImpl(
                properties, dockerService(Map.of()), new ObjectMapper());

        assertThrows(RuntimeException.class,
                () -> collector.collect(RunnerTaskMessage.builder().taskId(1002L).build(), ExperimentType.REDIS));
    }

    private DockerService dockerService(Map<String, String> stats) {
        return (DockerService) Proxy.newProxyInstance(
                DockerService.class.getClassLoader(),
                new Class<?>[] {DockerService.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "containerName" -> "ma-task-" + args[0] + "-" + args[1];
                    case "stats" -> stats.get(args[0].toString());
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
