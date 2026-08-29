package com.mware.experiment.biz.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mware.experiment.config.ExperimentRabbitConfig;
import com.mware.experiment.biz.log.TaskLogStore;
import com.mware.experiment.domain.ExperimentAnalysis;
import com.mware.experiment.domain.ExperimentResult;
import com.mware.experiment.domain.ExperimentTask;
import com.mware.experiment.mapper.ExperimentAnalysisMapper;
import com.mware.experiment.mapper.ExperimentResultMapper;
import com.mware.experiment.mapper.ExperimentTaskMapper;
import com.mware.experiment.mq.message.AgentAnalysisTaskMessage;
import com.mware.experiment.mq.message.RunnerTaskStatusMessage;
import com.mware.experiment.mq.producer.AgentAnalysisTaskProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

/** 将 Runner 回传的阶段、终态和指标保存到 experiment-service。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RunnerTaskStatusConsumer {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 1024;

    private final ExperimentTaskMapper experimentTaskMapper;
    private final ExperimentResultMapper experimentResultMapper;
    private final ExperimentAnalysisMapper experimentAnalysisMapper;
    private final AgentAnalysisTaskProducer agentAnalysisTaskProducer;
    private final ObjectMapper objectMapper;
    private final TaskLogStore taskLogStore;

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

        // experiment_task.error_message 是 VARCHAR(1024)，避免 Runner 的 Maven/Docker 长日志让状态消息反复重试。
        String errorMessage = message.getErrorMessage();
        if (errorMessage != null && errorMessage.length() > MAX_ERROR_MESSAGE_LENGTH) {
            errorMessage = errorMessage.substring(0, MAX_ERROR_MESSAGE_LENGTH - 3) + "...";
        }

        // 1. 只更新当前投递批次，SUCCESS / FAILED / CANCELLED 终态不能再被后续消息覆盖。
        LambdaUpdateWrapper<ExperimentTask> update = new LambdaUpdateWrapper<ExperimentTask>()
                .eq(ExperimentTask::getId, message.getTaskId())
                .eq(ExperimentTask::getDispatchId, message.getDispatchId())
                .notIn(ExperimentTask::getStatus, "SUCCESS", "FAILED", "CANCELLED")
                .set(ExperimentTask::getStatus, message.getStatus())
                .set(ExperimentTask::getCurrentStage, message.getCurrentStage())
                .set(ExperimentTask::getErrorCode, message.getErrorCode())
                .set(ExperimentTask::getErrorMessage, errorMessage)
                .set(ExperimentTask::getUpdatedAt, LocalDateTime.now());
        if (message.getProgress() != null) {
            update.set(ExperimentTask::getProgress, message.getProgress());
        }
        if ("RUNNING".equals(message.getStatus())) {
            update.setSql("started_at = COALESCE(started_at, NOW())");
        } else {
            update.set(ExperimentTask::getFinishedAt, occurredAt);
        }
        int updated = experimentTaskMapper.update(null, update);

        // 2. 只有当前 dispatch 的状态更新成功后，才把日志放入当前任务，避免旧投递串入新重试。
        if (updated == 1) {
            String level = "FAILED".equals(message.getStatus()) ? "ERROR" : "INFO";
            String logMessage = message.getLogMessage();
            if (logMessage == null || logMessage.isBlank()) {
                logMessage = message.getCurrentStage();
            }
            taskLogStore.append(message.getTaskId(), level, message.getCurrentStage(), logMessage,
                    message.getOccurredAtEpochMs());
        }

        // 3. SUCCESS 时把 Runner 返回的指标保存到 experiment_result；重复消息按 taskId 覆盖。
        if (updated == 1 && "SUCCESS".equals(message.getStatus())
                && message.getMetricsJson() != null && !message.getMetricsJson().isBlank()) {
            saveMetrics(message);
        }

        // 4. SUCCESS 且指标落库后，自动创建 Agent 分析任务并投递 MQ（幂等：一任务一分析）。
        if (updated == 1 && "SUCCESS".equals(message.getStatus())) {
            dispatchAutoAnalysis(message.getTaskId());
        }
    }

    /**
     * Runner 成功后自动创建 experiment_analysis（CREATED）并投递 Agent 分析任务，
     * 投递确认成功后推进 CREATED → QUEUED；投递失败把分析置 FAILED，
     * 由用户在任务详情页手动重新发起（autoAnalysisForTask 已保证一任务只有一条）。
     */
    private void dispatchAutoAnalysis(Long taskId) {
        long existing = experimentAnalysisMapper.selectCount(
                new LambdaQueryWrapper<ExperimentAnalysis>()
                        .eq(ExperimentAnalysis::getTaskId, taskId));
        if (existing > 0) {
            return;
        }
        ExperimentTask task = experimentTaskMapper.selectById(taskId);
        if (task == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        ExperimentAnalysis analysis = ExperimentAnalysis.builder()
                .taskId(taskId)
                .userId(task.getUserId())
                .versionId(task.getVersionId())
                .analysisType("PERFORMANCE_DIAGNOSIS")
                .triggerType("AUTO")
                .status("CREATED")
                .progress(0)
                .dispatchId(UUID.randomUUID().toString())
                .startedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
        experimentAnalysisMapper.insert(analysis);

        try {
            agentAnalysisTaskProducer.send(AgentAnalysisTaskMessage.builder()
                    .analysisId(analysis.getId())
                    .taskId(taskId)
                    .userId(task.getUserId())
                    .versionId(task.getVersionId())
                    .middlewareType(null)
                    .analysisType(analysis.getAnalysisType())
                    .triggerType("AUTO")
                    .dispatchId(analysis.getDispatchId())
                    .queuedAtEpochMs(System.currentTimeMillis())
                    .build());
        } catch (RuntimeException e) {
            // 投递失败：置 FAILED 并记录原因，用户可在任务详情重新发起分析
            experimentAnalysisMapper.update(null, new LambdaUpdateWrapper<ExperimentAnalysis>()
                    .eq(ExperimentAnalysis::getId, analysis.getId())
                    .set(ExperimentAnalysis::getStatus, "FAILED")
                    .set(ExperimentAnalysis::getErrorCode, "MQ_DISPATCH")
                    .set(ExperimentAnalysis::getErrorMessage, "分析任务投递失败: " + e.getMessage())
                    .set(ExperimentAnalysis::getUpdatedAt, LocalDateTime.now()));
            log.error("自动分析任务投递失败 taskId={}, analysisId={}", taskId, analysis.getId(), e);
            return;
        }

        // 投递确认成功：CREATED → QUEUED
        experimentAnalysisMapper.update(null, new LambdaUpdateWrapper<ExperimentAnalysis>()
                .eq(ExperimentAnalysis::getId, analysis.getId())
                .eq(ExperimentAnalysis::getStatus, "CREATED")
                .set(ExperimentAnalysis::getStatus, "QUEUED")
                .set(ExperimentAnalysis::getUpdatedAt, LocalDateTime.now()));
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
