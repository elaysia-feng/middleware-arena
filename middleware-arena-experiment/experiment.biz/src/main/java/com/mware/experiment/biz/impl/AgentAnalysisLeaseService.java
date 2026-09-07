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
 *   <li><b>抢占</b>：当前批次的 CREATED / QUEUED → ANALYZING 条件更新，只有一个消费者能抢到；</li>
 *   <li><b>续约</b>：Python 分析中每次 publish ANALYZING 都会刷新 updatedAt（Java 消费者落库）；</li>
 *   <li><b>回收</b>：本类的定时任务把 updatedAt 超过租约时长仍未进入终态的任务
 *       重新投递（Python 崩溃 / 网络分区后任务不丢），超过最大重派时限则置 FAILED 放弃。</li>
 * </ul>
 * <p>幂等保障：抢占和结果回传均校验 dispatchId，旧批次及终态后的消息直接忽略。</p>
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
     * Python 消费者凭消息批次抢租约；允许 CREATED，覆盖消息先于 Confirm 回写到达的窗口。
     *
     * @param analysisId 分析 ID
     * @param taskId 实验任务 ID
     * @param dispatchId 消息投递批次
     * @return true=抢到，可以开始分析；false=已被其他消费者抢占 / 状态不允许 / 记录不存在
     */
    public boolean claim(Long analysisId, Long taskId, String dispatchId) {
        // 1. 旧消息不能抢占新批次的租约。
        if (analysisId == null || taskId == null || dispatchId == null || dispatchId.isBlank()) {
            return false;
        }
        ExperimentAnalysis analysis = experimentAnalysisMapper.selectById(analysisId);
        if (analysis == null || !taskId.equals(analysis.getTaskId())) {
            return false;
        }
        // 2. 抢占条件由数据库原子判断，确认回写不能把 ANALYZING 降回 QUEUED。
        int updated = experimentAnalysisMapper.update(null, new LambdaUpdateWrapper<ExperimentAnalysis>()
                .eq(ExperimentAnalysis::getId, analysisId)
                .eq(ExperimentAnalysis::getTaskId, taskId)
                .eq(ExperimentAnalysis::getDispatchId, dispatchId)
                .in(ExperimentAnalysis::getStatus, "CREATED", "QUEUED")
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
        // 1. 同时恢复待投递、排队失联和执行失联记录，按最久未更新优先扫描。
        LocalDateTime now = LocalDateTime.now();
        List<ExperimentAnalysis> stale = experimentAnalysisMapper.selectList(
                new LambdaQueryWrapper<ExperimentAnalysis>()
                        .in(ExperimentAnalysis::getStatus, "CREATED", "QUEUED", "ANALYZING")
                        .lt(ExperimentAnalysis::getUpdatedAt, now.minusMinutes(leaseMinutes))
                        .lt(ExperimentAnalysis::getCreatedAt, now)
                        .orderByAsc(ExperimentAnalysis::getUpdatedAt, ExperimentAnalysis::getId)
                        .last("LIMIT 20"));
        // 2. 用查询时的批次和更新时间做条件更新，避免误回收刚续约或已完成的分析。
        for (ExperimentAnalysis analysis : stale) {
            // 超过最长重派窗口：放弃重派，置 FAILED（防止永久故障的目标无限循环烧 LLM 费用）
            if (analysis.getCreatedAt().isBefore(now.minusHours(maxRequeueHours))) {
                int expired = experimentAnalysisMapper.update(null, new LambdaUpdateWrapper<ExperimentAnalysis>()
                        .eq(ExperimentAnalysis::getId, analysis.getId())
                        .eq(ExperimentAnalysis::getStatus, analysis.getStatus())
                        .eq(ExperimentAnalysis::getDispatchId, analysis.getDispatchId())
                        .eq(ExperimentAnalysis::getUpdatedAt, analysis.getUpdatedAt())
                        .set(ExperimentAnalysis::getStatus, "FAILED")
                        .set(ExperimentAnalysis::getErrorCode, "LEASE_EXPIRED")
                        .set(ExperimentAnalysis::getErrorMessage, "分析多次重派仍未完成，已放弃")
                        .set(ExperimentAnalysis::getFinishedAt, now)
                        .set(ExperimentAnalysis::getUpdatedAt, now));
                if (expired == 1) {
                    log.error("analysis 超过最长重派窗口，置 FAILED: analysisId={}", analysis.getId());
                }
                continue;
            }

            // 先持久化新批次，发送时使用同一个值；失败仍保持可恢复的 CREATED。
            String dispatchId = UUID.randomUUID().toString();
            int updated = experimentAnalysisMapper.update(null, new LambdaUpdateWrapper<ExperimentAnalysis>()
                    .eq(ExperimentAnalysis::getId, analysis.getId())
                    .eq(ExperimentAnalysis::getStatus, analysis.getStatus())
                    .eq(ExperimentAnalysis::getDispatchId, analysis.getDispatchId())
                    .eq(ExperimentAnalysis::getUpdatedAt, analysis.getUpdatedAt())
                    .set(ExperimentAnalysis::getStatus, "CREATED")
                    .set(ExperimentAnalysis::getDispatchId, dispatchId)
                    .set(ExperimentAnalysis::getProgress, 0)
                    .set(ExperimentAnalysis::getCurrentStage, null)
                    .set(ExperimentAnalysis::getStartedAt, null)
                    .set(ExperimentAnalysis::getUpdatedAt, now));
            if (updated != 1) {
                continue;
            }
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
                        .dispatchId(dispatchId)
                        .queuedAtEpochMs(System.currentTimeMillis())
                        .build());
                experimentAnalysisMapper.update(null, new LambdaUpdateWrapper<ExperimentAnalysis>()
                        .eq(ExperimentAnalysis::getId, analysis.getId())
                        .eq(ExperimentAnalysis::getDispatchId, dispatchId)
                        .eq(ExperimentAnalysis::getStatus, "CREATED")
                        .set(ExperimentAnalysis::getStatus, "QUEUED")
                        .set(ExperimentAnalysis::getUpdatedAt, LocalDateTime.now()));
                log.warn("analysis 租约过期已重派: analysisId={}, leaseMinutes={}",
                        analysis.getId(), leaseMinutes);
            } catch (RuntimeException e) {
                log.error("analysis 重派投递失败: analysisId={}", analysis.getId(), e);
            }
        }
    }
}
