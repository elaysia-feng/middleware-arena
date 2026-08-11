package com.mware.runner.biz.progress;

import com.mware.runner.dto.RunnerTaskMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 阶段进度回传：build / run / waitHealthy / benchmark / collect / cleanup 各阶段开始时
 * 通知 experiment 更新 ExperimentTask 状态。
 * <p>
 * 当前实现只打日志（框架占位）。
 * TODO[Runner]：progress.enabled=true 时接入
 * <ul>
 *   <li>MQ：runner → experiment.task.exchange 投进度消息（与任务投递同拓扑），或</li>
 *   <li>Feign：调 experiment 更新任务状态接口。</li>
 * </ul>
 */
@Component
@Slf4j
public class ProgressReporter {

    /** 阶段名：BUILDING / RUNNING / WAITING_HEALTH / BENCHMARKING / COLLECTING / CLEANING */
    public void stage(RunnerTaskMessage message, String stage) {
        log.info("任务阶段回传 taskId={}, stage={}", message.getTaskId(), stage);
        // TODO[Runner]：progress.enabled=true 时经 progress.target 指定的通道回传
    }
}
