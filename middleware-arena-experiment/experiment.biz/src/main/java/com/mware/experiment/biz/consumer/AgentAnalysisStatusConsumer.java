package com.mware.experiment.biz.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mware.experiment.config.AgentRabbitConfig;
import com.mware.experiment.domain.ExperimentAnalysis;
import com.mware.experiment.mq.message.AgentAnalysisStatusMessage;
import com.mware.experiment.mapper.ExperimentAnalysisMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Python Agent → Experiment 的分析状态/结果消费者。
 *
 * <p>1. 监听 agent.analysis.status.queue，按 analysisId 幂等更新 experiment_analysis：
 *    终态（SUCCESS / FAILED）一旦落库，后续同 analysisId 的消息直接丢弃。</p>
 * <p>2. ANALYZING 只推进 currentStage / progress；SUCCESS 解析 resultJson 落
 *    bottleneck / confidence / evidence / hypotheses / suggestions / report；FAILED 落错误信息。</p>
 * <p>3. <b>ack 策略（定稿）</b>：MANUAL ack。消息本身非法（缺字段 / JSON 损坏 / analysis 不存在 /
 *    analysisId 与 taskId 不匹配）属毒消息，直接 basicAck 丢弃并打 ERROR 日志——
 *    状态消息可由 Python 端重新上报，且落库幂等，不需要独立 DLQ；
 *    数据库等瞬时故障走 basicNack(requeue) 让 Broker 重投，恢复后自动续上。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AgentAnalysisStatusConsumer {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 1024;

    private final ExperimentAnalysisMapper experimentAnalysisMapper;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = AgentRabbitConfig.QUEUE_STATUS, ackMode = "MANUAL")
    @Transactional
    public void onStatus(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        AgentAnalysisStatusMessage status;
        try {
            status = objectMapper.readValue(message.getBody(), AgentAnalysisStatusMessage.class);
        } catch (IOException e) {
            // 毒消息：JSON 损坏重试也不会成功，ack 丢弃避免堵队列（定稿：状态队列不建独立 DLQ）
            log.error("analysis status message JSON 损坏，丢弃: {}",
                    new String(message.getBody()), e);
            channel.basicAck(deliveryTag, false);
            return;
        }

        // 1. 字段校验：analysisId / taskId / status 是路由和幂等的最低要求
        if (status.getAnalysisId() == null || status.getTaskId() == null || status.getStatus() == null) {
            log.error("analysis status message 缺少 analysisId/taskId/status，丢弃: {}", status);
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            applyStatus(status);
            channel.basicAck(deliveryTag, false);
        } catch (IllegalArgumentException e) {
            // 校验类失败（analysis 不存在 / analysisId 与 taskId 不匹配）：重试无意义，ack 丢弃
            log.error("analysis status message 校验失败，丢弃: analysisId={}, reason={}",
                    status.getAnalysisId(), e.getMessage());
            channel.basicAck(deliveryTag, false);
        } catch (RuntimeException e) {
            // 数据库等瞬时故障：nack requeue，Broker 重投，恢复后自动续上
            log.error("analysis status message 处理失败，requeue: analysisId={}",
                    status.getAnalysisId(), e);
            channel.basicNack(deliveryTag, false, true);
        }
    }

    /**
     * 按 analysisId 幂等更新：终态（SUCCESS / FAILED）落库后不再被覆盖，
     * 条件更新行数为 0 说明已终态化或记录不存在，直接返回。
     */
    private void applyStatus(AgentAnalysisStatusMessage status) {
        ExperimentAnalysis analysis = experimentAnalysisMapper.selectById(status.getAnalysisId());
        if (analysis == null) {
            throw new IllegalArgumentException("experiment_analysis 不存在: " + status.getAnalysisId());
        }
        // analysisId 与 taskId 必须对应，防止消息串任务
        if (!status.getTaskId().equals(analysis.getTaskId())) {
            throw new IllegalArgumentException("analysisId=" + status.getAnalysisId()
                    + " 不属于 taskId=" + status.getTaskId());
        }
        if ("SUCCESS".equals(analysis.getStatus()) || "FAILED".equals(analysis.getStatus())) {
            log.info("analysis {} 已终态({})，忽略后续状态消息: {}",
                    status.getAnalysisId(), analysis.getStatus(), status.getStatus());
            return;
        }

        LambdaUpdateWrapper<ExperimentAnalysis> update = new LambdaUpdateWrapper<ExperimentAnalysis>()
                .eq(ExperimentAnalysis::getId, status.getAnalysisId())
                .set(ExperimentAnalysis::getStatus, status.getStatus())
                .set(ExperimentAnalysis::getUpdatedAt, LocalDateTime.now());
        if ("ANALYZING".equals(status.getStatus())) {
            // 进行中：只推进阶段与进度，结构化字段留给终态消息
            update.set(ExperimentAnalysis::getCurrentStage, status.getCurrentStage());
            if (status.getProgress() != null) {
                update.set(ExperimentAnalysis::getProgress, status.getProgress());
            }
        } else if ("SUCCESS".equals(status.getStatus())) {
            applySuccess(update, status);
        } else if ("FAILED".equals(status.getStatus())) {
            applyFailed(update, status);
        } else {
            throw new IllegalArgumentException("未知的 analysis 状态: " + status.getStatus());
        }
        experimentAnalysisMapper.update(null, update);
    }

    /** SUCCESS：解析 resultJson 落结构化诊断结果；resultJson 损坏视为毒消息（丢弃） */
    private void applySuccess(LambdaUpdateWrapper<ExperimentAnalysis> update,
            AgentAnalysisStatusMessage status) {
        String resultJson = status.getResultJson();
        if (resultJson != null && !resultJson.isBlank()) {
            try {
                JsonNode result = objectMapper.readTree(resultJson);
                update.set(ExperimentAnalysis::getBottleneckType, textOrNull(result, "bottleneck"));
                JsonNode confidence = result.get("confidence");
                if (confidence != null && confidence.isNumber()) {
                    update.set(ExperimentAnalysis::getConfidence, confidence.asDouble());
                }
                update.set(ExperimentAnalysis::getEvidenceJson, jsonOrNull(result, "evidence"));
                update.set(ExperimentAnalysis::getHypothesesJson, jsonOrNull(result, "hypotheses"));
                update.set(ExperimentAnalysis::getSuggestionsJson, jsonOrNull(result, "suggestions"));
                update.set(ExperimentAnalysis::getReport, textOrNull(result, "report"));
                update.set(ExperimentAnalysis::getModelName, textOrNull(result, "modelName"));
                update.set(ExperimentAnalysis::getLangfuseTraceId, textOrNull(result, "langfuseTraceId"));
            } catch (IOException e) {
                throw new IllegalArgumentException(
                        "resultJson 格式错误，analysisId=" + status.getAnalysisId(), e);
            }
        }
        update.set(ExperimentAnalysis::getFinishedAt, finishedAt(status));
    }

    /** FAILED：落 errorCode / errorMessage；error_message 列有长度限制则截断 */
    private void applyFailed(LambdaUpdateWrapper<ExperimentAnalysis> update,
            AgentAnalysisStatusMessage status) {
        String errorMessage = status.getErrorMessage();
        if (errorMessage != null && errorMessage.length() > MAX_ERROR_MESSAGE_LENGTH) {
            errorMessage = errorMessage.substring(0, MAX_ERROR_MESSAGE_LENGTH - 3) + "...";
        }
        update.set(ExperimentAnalysis::getErrorCode, status.getErrorCode());
        update.set(ExperimentAnalysis::getErrorMessage, errorMessage);
        update.set(ExperimentAnalysis::getFinishedAt, finishedAt(status));
    }

    private LocalDateTime finishedAt(AgentAnalysisStatusMessage status) {
        return status.getFinishedAtEpochMs() == null
                ? LocalDateTime.now()
                : Instant.ofEpochMilli(status.getFinishedAtEpochMs())
                        .atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    /** 对象 / 数组字段原样序列化回 String（落 JSON 列） */
    private String jsonOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.toString();
    }
}
