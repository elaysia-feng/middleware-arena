package com.mware.experiment.biz.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mware.experiment.biz.log.TaskLogStore;
import com.mware.experiment.domain.ExperimentTask;
import com.mware.experiment.mapper.ExperimentTaskMapper;
import com.mware.experiment.mq.producer.RunnerDispatchCompensator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Runner 任务消息投递失败补偿。
 *
 * <p>触发点：生产者 Publisher Confirm 回调 nack / mandatory return，以及 send() 同步失败路径。
 * 幂等保障：条件更新只命中 CREATED / QUEUED（CREATE 失败）或 CANCELLED（CANCEL 失败）状态，
 * confirm 回调与同步路径对同一任务重复触发时第二次更新行数为 0，直接跳过。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RunnerDispatchCompensation implements RunnerDispatchCompensator {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 1024;

    private final ExperimentTaskMapper experimentTaskMapper;
    private final TaskLogStore taskLogStore;

    @Override
    public void onDispatchFailed(Long taskId, String dispatchType, String reason) {
        if (taskId == null) {
            return;
        }
        if ("CANCEL".equals(dispatchType)) {
            compensateCancel(taskId, reason);
            return;
        }
        compensateCreate(taskId, reason);
    }

    /**
     * CREATE 投递失败：任务置 FAILED（errorCode=MQ_DISPATCH），用户可在任务列表点"重试"
     * 走已有的 retryTask 流程重新入队；不自动重投，避免 Broker 故障期间反复压消息。
     */
    private void compensateCreate(Long taskId, String reason) {
        int updated = experimentTaskMapper.update(null, new LambdaUpdateWrapper<ExperimentTask>()
                .eq(ExperimentTask::getId, taskId)
                .in(ExperimentTask::getStatus, "CREATED", "QUEUED")
                .set(ExperimentTask::getStatus, "FAILED")
                .set(ExperimentTask::getErrorCode, "MQ_DISPATCH")
                .set(ExperimentTask::getErrorMessage, truncate("MQ 投递失败: " + reason))
                .set(ExperimentTask::getUpdatedAt, LocalDateTime.now()));
        if (updated == 1) {
            taskLogStore.append(taskId, "ERROR", "DISPATCH",
                    "任务投递到 Runner 失败（" + reason + "），已置为失败，可点击重试", null);
            log.error("task dispatch compensated: taskId={}, reason={}", taskId, reason);
        }
    }

    /**
     * CANCEL 投递失败：任务在 DB 已是 CANCELLED 但 Runner 侧没收到取消消息。
     * 这里不改状态（无法确定 Runner 实际进度，回退 QUEUED 会与真实执行冲突），
     * 只落告警备注 + 错误日志，由运维按告警介入。
     */
    private void compensateCancel(Long taskId, String reason) {
        int updated = experimentTaskMapper.update(null, new LambdaUpdateWrapper<ExperimentTask>()
                .eq(ExperimentTask::getId, taskId)
                .eq(ExperimentTask::getStatus, "CANCELLED")
                .set(ExperimentTask::getErrorCode, "CANCEL_DISPATCH")
                .set(ExperimentTask::getErrorMessage, truncate("取消消息投递失败: " + reason))
                .set(ExperimentTask::getUpdatedAt, LocalDateTime.now()));
        if (updated == 1) {
            taskLogStore.append(taskId, "ERROR", "DISPATCH",
                    "取消消息投递失败（" + reason + "），任务状态可能与 Runner 实际进度不一致，需要人工确认", null);
            log.error("cancel dispatch alarm: taskId={}, reason={}", taskId, reason);
        }
    }

    private String truncate(String message) {
        return message.length() > MAX_ERROR_MESSAGE_LENGTH
                ? message.substring(0, MAX_ERROR_MESSAGE_LENGTH - 3) + "..."
                : message;
    }
}
