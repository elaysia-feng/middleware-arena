package com.mware.runner.biz.progress.impl;

import com.mware.runner.biz.progress.ProgressReporter;
import com.mware.runner.dto.RunnerTaskMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 阶段进度回传实现：当前只打日志（框架占位）。
 */
@Component
@Slf4j
public class ProgressReporterImpl implements ProgressReporter {

    @Override
    public void stage(RunnerTaskMessage message, String stage) {
        log.info("任务阶段回传 taskId={}, stage={}", message.getTaskId(), stage);
        // TODO[Runner]：progress.enabled=true 时经 progress.target 指定的通道回传
    }
}
