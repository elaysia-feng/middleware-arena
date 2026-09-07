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
import com.mware.experiment.domain.ExperimentVersion;
import com.mware.experiment.domain.ExperimentTemplate;
import com.mware.experiment.mapper.ExperimentAnalysisMapper;
import com.mware.experiment.mapper.ExperimentResultMapper;
import com.mware.experiment.mapper.ExperimentTaskMapper;
import com.mware.experiment.mapper.ExperimentVersionMapper;
import com.mware.experiment.mapper.ExperimentTemplateMapper;
import com.mware.experiment.mq.message.AgentAnalysisTaskMessage;
import com.mware.experiment.mq.message.RunnerTaskStatusMessage;
import com.mware.experiment.mq.producer.AgentAnalysisTaskProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    private final ExperimentVersionMapper experimentVersionMapper;
    private final ExperimentTemplateMapper experimentTemplateMapper;
    private final PlatformTransactionManager transactionManager;

    /**
     * 提交任务状态、指标和分析记录后，再向 Agent 投递消息。
     * @param message Runner 当前批次的状态
     */
    @RabbitListener(queues = ExperimentRabbitConfig.QUEUE_STATUS)
    public void onStatus(RunnerTaskStatusMessage message) {
        // 1. 只接受 Runner 支持的状态，避免未知状态写入完成时间。
        if (message == null || message.getTaskId() == null || message.getDispatchId() == null
                || message.getStatus() == null
                || !java.util.Set.of("RUNNING", "SUCCESS", "FAILED", "CANCELLED").contains(message.getStatus())
                || (message.getProgress() != null && (message.getProgress() < 0 || message.getProgress() > 100))) {
            return;
        }

        // 2. 本地数据库改动一起提交，慢速 Publisher Confirm 不占用数据库事务。
        AgentAnalysisTaskMessage analysisMessage = new TransactionTemplate(transactionManager)
                .execute(transaction -> applyStatus(message));
        if (analysisMessage == null) {
            return;
        }

        // 3. 投递失败保留 CREATED，由租约扫描器补投；不能把已执行的分析误置失败。
        try {
            agentAnalysisTaskProducer.send(analysisMessage);
            experimentAnalysisMapper.update(null, new LambdaUpdateWrapper<ExperimentAnalysis>()
                    .eq(ExperimentAnalysis::getId, analysisMessage.getAnalysisId())
                    .eq(ExperimentAnalysis::getDispatchId, analysisMessage.getDispatchId())
                    .eq(ExperimentAnalysis::getStatus, "CREATED")
                    .set(ExperimentAnalysis::getStatus, "QUEUED")
                    .set(ExperimentAnalysis::getUpdatedAt, LocalDateTime.now()));
        } catch (RuntimeException e) {
            log.error("自动分析投递或确认失败，等待补投 taskId={}, analysisId={}",
                    message.getTaskId(), analysisMessage.getAnalysisId(), e);
        }
    }

    /** 在本地事务中保存状态，并返回提交后需要投递的分析消息。 */
    private AgentAnalysisTaskMessage applyStatus(RunnerTaskStatusMessage message) {
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
            String committedLogMessage = logMessage;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                /** 提交后才展示日志，避免回滚留下虚假的成功记录。 */
                @Override
                public void afterCommit() {
                    taskLogStore.append(message.getTaskId(), level, message.getCurrentStage(), committedLogMessage,
                            message.getOccurredAtEpochMs());
                }
            });
        }

        // 3. SUCCESS 时把 Runner 返回的指标保存到 experiment_result；重复消息按 taskId 覆盖。
        if (updated == 1 && "SUCCESS".equals(message.getStatus())
                && message.getMetricsJson() != null && !message.getMetricsJson().isBlank()) {
            saveMetrics(message);
        }

        // 4. SUCCESS 且指标落库后，自动创建 Agent 分析任务并投递 MQ（幂等：一任务一分析）。
        if (updated == 1 && "SUCCESS".equals(message.getStatus())) {
            return dispatchAutoAnalysis(message.getTaskId());
        }
        return null;
    }

    /**
     * 创建分析记录并组装消息，投递由调用方在事务提交后执行。
     */
    private AgentAnalysisTaskMessage dispatchAutoAnalysis(Long taskId) {
        // 1. 分析必须关联真实版本及模板，才能生成 Python 可接受的中间件类型。
        long existing = experimentAnalysisMapper.selectCount(
                new LambdaQueryWrapper<ExperimentAnalysis>()
                        .eq(ExperimentAnalysis::getTaskId, taskId));
        if (existing > 0) {
            return null;
        }
        ExperimentTask task = experimentTaskMapper.selectById(taskId);
        if (task == null) {
            return null;
        }
        ExperimentVersion version = experimentVersionMapper.selectById(task.getVersionId());
        ExperimentTemplate template = version == null ? null
                : experimentTemplateMapper.selectById(version.getTemplateId());
        if (template == null || template.getMiddlewareType() == null || template.getMiddlewareType().isBlank()) {
            log.error("自动分析缺少模板中间件类型 taskId={}", taskId);
            return null;
        }

        // 2. CREATED 是持久化待投递记录，进程在提交后退出仍可由扫描器恢复。
        LocalDateTime now = LocalDateTime.now();
        ExperimentAnalysis analysis = ExperimentAnalysis.builder()
                .taskId(taskId)
                .userId(task.getUserId())
                .versionId(task.getVersionId())
                .middlewareType(template.getMiddlewareType())
                .analysisType("PERFORMANCE_DIAGNOSIS")
                .triggerType("AUTO")
                .status("CREATED")
                .progress(0)
                .dispatchId(UUID.randomUUID().toString())
                .createdAt(now)
                .updatedAt(now)
                .build();
        experimentAnalysisMapper.insert(analysis);

        return AgentAnalysisTaskMessage.builder()
                    .analysisId(analysis.getId())
                    .taskId(taskId)
                    .userId(task.getUserId())
                    .versionId(task.getVersionId())
                    .middlewareType(analysis.getMiddlewareType())
                    .analysisType(analysis.getAnalysisType())
                    .triggerType("AUTO")
                    .dispatchId(analysis.getDispatchId())
                    .queuedAtEpochMs(System.currentTimeMillis())
                    .build();
    }

    private void saveMetrics(RunnerTaskStatusMessage message) {
        // 1. 验证 JSON 对象结构，避免 null 或数组被解释成全零指标。
        try {
            JsonNode metrics = objectMapper.readTree(message.getMetricsJson());
            if (metrics == null || !metrics.isObject()) {
                throw new IllegalArgumentException("Runner metricsJson 必须是 JSON 对象");
            }
            ExperimentResult result = experimentResultMapper.selectOne(
                    new LambdaQueryWrapper<ExperimentResult>()
                            .eq(ExperimentResult::getTaskId, message.getTaskId()));
            if (result == null) {
                result = ExperimentResult.builder()
                        .taskId(message.getTaskId())
                        .createdAt(LocalDateTime.now())
                        .build();
            }
            // 2. 指标与任务终态在同一事务中提交。
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
