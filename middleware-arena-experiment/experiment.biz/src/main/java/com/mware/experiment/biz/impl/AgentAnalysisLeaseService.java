package com.mware.experiment.biz.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mware.experiment.domain.ExperimentAnalysis;
import com.mware.experiment.mapper.ExperimentAnalysisMapper;
import com.mware.experiment.mq.message.AgentAnalysisTaskMessage;
import com.mware.experiment.mq.producer.AgentAnalysisTaskProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Agent 分析任务租约服务（消费端"快速 ack"重构的配套件）。
 *
 * <p>背景：Python 消费者改为"收到任务 → 向 Java 抢租约 → 立刻 ack 原消息 → 后台执行"，
 * MQ 不再被长耗时分析握住。租约本体就是 experiment_analysis 的状态 + updatedAt：</p>
 * <ul>
 *   <li><b>抢占</b>：QUEUED → ANALYZING 的条件更新，只有一个消费者能抢到（乐观锁）；</li>
 *   <li><b>续约</b>：Python 分析中每次 publish ANALYZING 都会刷新 updatedAt（Java 消费者落库）；</li>
 *   <li><b>回收</b>：本类的定时任务把 updatedAt 超过租约时长仍停留在 ANALYZING 的任务
 *       重新投递（Python 崩溃 / 网络分区后任务不丢），超过最大重派时限则置 FAILED 放弃。</li>
 * </ul>
 * <p>幂等保障：重复执行的分析都会发终态消息，Java 消费者对终态后的消息直接忽略。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentAnalysisLeaseService {

    private final ExperimentAnalysisMapper experimentAnalysisMapper;
    private final AgentAnalysisTaskProducer agentAnalysisTaskProducer;

    /** 分析租约时长：ANALYZING 状态超过该分钟数没有任何更新即视为持有者已失联 */
    @Value("${agent.analysis.lease-minutes:30}")
    private int leaseMinutes;

    /** 从分析创建起的最长重派窗口：超过后不再重派，置 FAILED（避免无限循环烧 LLM 费用） */
    @Value("${agent.analysis.max-requeue-hours:24}")
    private int maxRequeueHours;

    /**
     * Python 消费者抢租约：QUEUED → ANALYZING 条件更新。
     *
     * @return true=抢到，可以开始分析；false=已被其他消费者抢占 / 状态不允许 / 记录不存在
     */
    public boolean claim(Long analysisId, Long taskId) {
        ExperimentAnalysis analysis = experimentAnalysisMapper.selectById(analysisId);
        if (analysis == null || !taskId.equals(analysis.getTaskId())) {
            return false;
        }
        int updated = experimentAnalysisMapper.update(null, new LambdaUpdateWrapper<ExperimentAnalysis>()
                .eq(ExperimentAnalysis::getId, analysisId)
                .eq(ExperimentAnalysis::getStatus, "QUEUED")
                .set(ExperimentAnalysis::getStatus, "ANALYZING")
                .set(ExperimentAnalysis::getCurrentStage, "LOAD_CONTEXT")
                .set(ExperimentAnalysis::getProgress, 5)
                .setSql("started_at = COALESCE(started_at, NOW())")
                .set(ExperimentAnalysis::getUpdatedAt, LocalDateTime.now()));
        if (updated != 1) {
            log.info("analysis lease claim 失败（已被抢占或状态不允许）: analysisId={}", analysisId);
        }
        return updated == 1;
    }

    /**
     * 定时回收失联分析的租约并重新投递。
     * 场景：Python 消费者抢到租约并 ack 了 MQ 消息后进程崩溃——
     * 没有重派机制的话这条分析会永远停在 ANALYZING。
     */
    @Scheduled(fixedDelayString = "${agent.analysis.reclaim-interval-ms:60000}")
    public void reclaimStaleAnalyses() {
        LocalDateTime now = LocalDateTime.now();
        List<ExperimentAnalysis> stale = experimentAnalysisMapper.selectList(
                new LambdaQueryWrapper<ExperimentAnalysis>()
                        .eq(ExperimentAnalysis::getStatus, "ANALYZING")
                        .lt(ExperimentAnalysis::getUpdatedAt, now.minusMinutes(leaseMinutes))
                        .lt(ExperimentAnalysis::getCreatedAt, now)
                        .last("LIMIT 20"));
        for (ExperimentAnalysis analysis : stale) {
            // 超过最长重派窗口：放弃重派，置 FAILED（防止永久故障的目标无限循环烧 LLM 费用）
            if (analysis.getCreatedAt().isBefore(now.minusHours(maxRequeueHours))) {
                experimentAnalysisMapper.update(null, new LambdaUpdateWrapper<ExperimentAnalysis>()
                        .eq(ExperimentAnalysis::getId, analysis.getId())
                        .eq(ExperimentAnalysis::getStatus, "ANALYZING")
                        .set(ExperimentAnalysis::getStatus, "FAILED")
                        .set(ExperimentAnalysis::getErrorCode, "LEASE_EXPIRED")
                        .set(ExperimentAnalysis::getErrorMessage, "分析多次重派仍未完成，已放弃")
                        .set(ExperimentAnalysis::getUpdatedAt, now));
                log.error("analysis 超过最长重派窗口，置 FAILED: analysisId={}", analysis.getId());
                continue;
            }

            // 重新生成 dispatchId 并重投任务消息；状态退回 QUEUED 让 Python 重新抢租约
            experimentAnalysisMapper.update(null, new LambdaUpdateWrapper<ExperimentAnalysis>()
                    .eq(ExperimentAnalysis::getId, analysis.getId())
                    .eq(ExperimentAnalysis::getStatus, "ANALYZING")
                    .set(ExperimentAnalysis::getStatus, "QUEUED")
                    .set(ExperimentAnalysis::getDispatchId, UUID.randomUUID().toString())
                    .set(ExperimentAnalysis::getUpdatedAt, now));
            try {
                agentAnalysisTaskProducer.send(AgentAnalysisTaskMessage.builder()
                        .analysisId(analysis.getId())
                        .taskId(analysis.getTaskId())
                        .userId(analysis.getUserId())
                        .versionId(analysis.getVersionId())
                        .baselineTaskId(analysis.getBaselineTaskId())
                        .middlewareType(analysis.getMiddlewareType())
                        .analysisType(analysis.getAnalysisType())
                        .triggerType(analysis.getTriggerType())
                        .dispatchId(analysis.getDispatchId())
                        .queuedAtEpochMs(System.currentTimeMillis())
                        .build());
                log.warn("analysis 租约过期已重派: analysisId={}, leaseMinutes={}",
                        analysis.getId(), leaseMinutes);
            } catch (RuntimeException e) {
                log.error("analysis 重派投递失败: analysisId={}", analysis.getId(), e);
            }
        }
    }
}
