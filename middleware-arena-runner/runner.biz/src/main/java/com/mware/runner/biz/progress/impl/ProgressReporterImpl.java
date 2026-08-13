package com.mware.runner.biz.progress.impl;

import com.mware.runner.biz.progress.ProgressReporter;
import com.mware.runner.biz.progress.RunnerTaskStatusProducer;
import com.mware.runner.dto.RunnerTaskMessage;
import com.mware.runner.dto.RunnerTaskStatusMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 通过 MQ 回传任务阶段、成功结果和失败原因。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProgressReporterImpl implements ProgressReporter {

    private final RunnerTaskStatusProducer statusProducer;

    @Override
    public void stage(RunnerTaskMessage message, String stage) {
        log.info("任务阶段回传 taskId={}, stage={}", message.getTaskId(), stage);
        statusProducer.send(RunnerTaskStatusMessage.builder()
                .taskId(message.getTaskId())
                .dispatchId(message.getDispatchId())
                .status("RUNNING")
                .currentStage(stage)
                .occurredAtEpochMs(System.currentTimeMillis())
                .build());
    }

    @Override
    public void completed(RunnerTaskMessage message, String metricsJson) {
        statusProducer.send(RunnerTaskStatusMessage.builder()
                .taskId(message.getTaskId())
                .dispatchId(message.getDispatchId())
                .status("SUCCESS")
                .currentStage("COMPLETED")
                .metricsJson(metricsJson)
                .occurredAtEpochMs(System.currentTimeMillis())
                .build());
    }

    @Override
    public void failed(RunnerTaskMessage message, Throwable error) {
        statusProducer.send(RunnerTaskStatusMessage.builder()
                .taskId(message.getTaskId())
                .dispatchId(message.getDispatchId())
                .status("FAILED")
                .currentStage("FAILED")
                .errorCode(error.getClass().getSimpleName())
                .errorMessage(error.getMessage())
                .occurredAtEpochMs(System.currentTimeMillis())
                .build());
    }
}
