package com.mware.experiment.biz.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mware.experiment.config.ExperimentRabbitConfig;
import com.mware.experiment.domain.ExperimentResult;
import com.mware.experiment.domain.ExperimentTask;
import com.mware.experiment.mapper.ExperimentResultMapper;
import com.mware.experiment.mapper.ExperimentTaskMapper;
import com.mware.experiment.mq.message.RunnerTaskStatusMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;

/** 将 Runner 回传的阶段、终态和指标保存到 experiment-service。 */
@Component
@RequiredArgsConstructor
public class RunnerTaskStatusConsumer {

    private final ExperimentTaskMapper experimentTaskMapper;
    private final ExperimentResultMapper experimentResultMapper;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = ExperimentRabbitConfig.QUEUE_STATUS)
    @Transactional
    public void onStatus(RunnerTaskStatusMessage message) {
        if (message.getTaskId() == null || message.getDispatchId() == null || message.getStatus() == null) {
            return;
        }

        LocalDateTime occurredAt = message.getOccurredAtEpochMs() == null
                ? LocalDateTime.now()
                : java.time.Instant.ofEpochMilli(message.getOccurredAtEpochMs())
                        .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();

        // 1. 只更新当前投递批次，SUCCESS / FAILED / CANCELLED 终态不能再被后续消息覆盖。
        LambdaUpdateWrapper<ExperimentTask> update = new LambdaUpdateWrapper<ExperimentTask>()
                .eq(ExperimentTask::getId, message.getTaskId())
                .eq(ExperimentTask::getDispatchId, message.getDispatchId())
                .notIn(ExperimentTask::getStatus, "SUCCESS", "FAILED", "CANCELLED")
                .set(ExperimentTask::getStatus, message.getStatus())
                .set(ExperimentTask::getCurrentStage, message.getCurrentStage())
                .set(ExperimentTask::getErrorCode, message.getErrorCode())
                .set(ExperimentTask::getErrorMessage, message.getErrorMessage())
                .set(ExperimentTask::getUpdatedAt, LocalDateTime.now());
        if ("RUNNING".equals(message.getStatus())) {
            update.setSql("started_at = COALESCE(started_at, NOW())");
        } else {
            update.set(ExperimentTask::getFinishedAt, occurredAt);
        }
        int updated = experimentTaskMapper.update(null, update);

        // 2. SUCCESS 时把 Runner 返回的指标保存到 experiment_result；重复消息按 taskId 覆盖。
        if (updated == 1 && "SUCCESS".equals(message.getStatus())
                && message.getMetricsJson() != null && !message.getMetricsJson().isBlank()) {
            saveMetrics(message);
        }
    }

    private void saveMetrics(RunnerTaskStatusMessage message) {
        try {
            JsonNode metrics = objectMapper.readTree(message.getMetricsJson());
            ExperimentResult result = experimentResultMapper.selectOne(
                    new LambdaQueryWrapper<ExperimentResult>()
                            .eq(ExperimentResult::getTaskId, message.getTaskId()));
            if (result == null) {
                result = ExperimentResult.builder()
                        .taskId(message.getTaskId())
                        .createdAt(LocalDateTime.now())
                        .build();
            }
            result.setQps(metrics.path("qps").asDouble());
            result.setP95Ms(metrics.path("p95Ms").asLong());
            result.setErrorRate(metrics.path("errorRate").asDouble());
            result.setAvgCpu(metrics.path("cpu").asDouble());
            result.setPeakMemoryMb(metrics.path("memMb").asLong());
            result.setMetricsJson(message.getMetricsJson());
            if (result.getId() == null) {
                experimentResultMapper.insert(result);
            } else {
                experimentResultMapper.updateById(result);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Runner metricsJson 格式错误，taskId=" + message.getTaskId(), e);
        }
    }
}
