package com.mware.runner.biz.benchmark.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mware.runner.biz.config.ExperimentType;
import com.mware.runner.biz.config.RunnerProperties;
import com.mware.runner.dto.RunnerTaskMessage;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class K6RunnerImplTest {

    @Test
    void buildScriptShouldContainStagesRequestAndSummary() throws Exception {
        K6RunnerImpl runner = new K6RunnerImpl(new RunnerProperties(), null, new ObjectMapper());
        K6RunnerImpl.BenchmarkPlan plan = new K6RunnerImpl.BenchmarkPlan(
                5, 10, 10, 20, 20, 30,
                new ObjectMapper().readTree("{\"productId\":1}"),
                Map.of("Content-Type", "application/json"));

        String script = runner.buildScript(
                1001L, ExperimentType.REDIS, "http://ma-task-1001-product:8080", plan);

        assertTrue(script.contains("http://ma-task-1001-product:8080/product/1"));
        assertTrue(script.contains("{ duration: '10s', target: 5 }"));
        assertTrue(script.contains("{ duration: '30s', target: 20 }"));
        assertTrue(script.contains("http_req_failed: ['rate<0.05']"));
        assertTrue(script.contains("/scripts/summary-1001.json"));
        assertTrue(script.contains("\"productId\":1"));
        assertTrue(script.contains("requestBody === null ? null : JSON.stringify(requestBody)"));
    }

    @Test
    void benchmarkPlanShouldLimitFreeAndVipConcurrency() {
        K6RunnerImpl runner = new K6RunnerImpl(new RunnerProperties(), null, new ObjectMapper());

        K6RunnerImpl.BenchmarkPlan freePlan = runner.benchmarkPlan(RunnerTaskMessage.builder()
                .taskId(1L)
                .tier("FREE")
                .runParamsJson("{\"concurrencyLadder\":[100,300,500],\"duration\":\"2m\"}")
                .build());
        K6RunnerImpl.BenchmarkPlan vipPlan = runner.benchmarkPlan(RunnerTaskMessage.builder()
                .taskId(2L)
                .tier("VIP")
                .runParamsJson("{\"concurrencyLadder\":[100,300,500],\"duration\":\"2m\"}")
                .build());

        assertEquals(20, freePlan.formalVus());
        assertEquals(200, vipPlan.formalVus());
        assertEquals(120, vipPlan.formalSeconds());
    }

    @Test
    void benchmarkPlanShouldRejectInvalidDuration() {
        K6RunnerImpl runner = new K6RunnerImpl(new RunnerProperties(), null, new ObjectMapper());
        RunnerTaskMessage message = RunnerTaskMessage.builder()
                .taskId(3L)
                .tier("VIP")
                .runParamsJson("{\"duration\":\"forever\"}")
                .build();

        assertThrows(IllegalArgumentException.class, () -> runner.benchmarkPlan(message));
    }
}
