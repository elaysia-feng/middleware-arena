package com.mware.runner.biz.execution.impl;

import com.mware.runner.biz.benchmark.K6Runner;
import com.mware.runner.biz.build.SutBuilder;
import com.mware.runner.biz.config.ExperimentType;
import com.mware.runner.biz.config.RunnerProperties;
import com.mware.runner.biz.docker.DockerService;
import com.mware.runner.biz.docker.ExperimentEnvironment;
import com.mware.runner.biz.metrics.MetricsCollector;
import com.mware.runner.biz.progress.ProgressReporter;
import com.mware.runner.dto.RunnerTaskMessage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RunnerServiceImplTest {

    @Test
    void stagesShouldRunInOrderAndCleanupCandidateImage() {
        List<String> calls = new ArrayList<>();
        SutBuilder sutBuilder = (message, type) -> {
            calls.add("build");
            return "ma-task-1001-sut:latest";
        };
        ExperimentEnvironment environment = new ExperimentEnvironment() {
            @Override
            public String start(RunnerTaskMessage message, ExperimentType type, String sutImage) {
                calls.add("start");
                return "http://ma-task-1001-product:8080";
            }

            @Override
            public void teardown(RunnerTaskMessage message, ExperimentType type) {
                calls.add("teardown");
            }
        };
        DockerService dockerService = (DockerService) Proxy.newProxyInstance(
                DockerService.class.getClassLoader(),
                new Class<?>[] {DockerService.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "waitHealthy" -> {
                        calls.add("health");
                        yield true;
                    }
                    case "removeImage" -> {
                        calls.add("removeImage");
                        yield null;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        K6Runner k6Runner = (message, type, baseUrl) -> calls.add("k6");
        MetricsCollector metricsCollector = (message, type) -> {
            calls.add("metrics");
            return "{\"qps\":100}";
        };
        ProgressReporter progressReporter = new ProgressReporter() {
            @Override
            public void stage(RunnerTaskMessage message, String stage) {
            }

            @Override
            public void completed(RunnerTaskMessage message, String metricsJson) {
            }

            @Override
            public void failed(RunnerTaskMessage message, Throwable error) {
            }
        };
        RunnerServiceImpl service = new RunnerServiceImpl(
                null, null, null, null, null,
                sutBuilder, environment, dockerService, k6Runner,
                metricsCollector, progressReporter, new RunnerProperties());
        RunnerTaskMessage message = RunnerTaskMessage.builder()
                .taskId(1001L)
                .middlewareType("redis")
                .baseline(false)
                .build();

        service.build(message);
        assertEquals("BUILDING", service.getTaskStatus(1001L));
        service.run(message);
        service.waitHealthy(message);
        service.benchmark(message);
        service.collectMetrics(message);
        service.cleanup(message);

        assertIterableEquals(
                List.of("build", "start", "health", "k6", "metrics", "teardown", "removeImage"),
                calls);
        assertNull(service.getTaskStatus(1001L));
    }
}
