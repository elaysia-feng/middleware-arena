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
                .progress(stageProgress(stage))
                .logMessage(stageMessage(stage))
                .occurredAtEpochMs(System.currentTimeMillis())
                .build());
    }

    @Override
    public void detail(RunnerTaskMessage message, String stage, String detail) {
        try {
            statusProducer.send(RunnerTaskStatusMessage.builder()
                    .taskId(message.getTaskId())
                    .dispatchId(message.getDispatchId())
                    .status("RUNNING")
                    .currentStage(stage)
                    .logMessage(detail)
                    .occurredAtEpochMs(System.currentTimeMillis())
                    .build());
        } catch (RuntimeException e) {
            log.warn("任务细节日志回传失败 taskId={}, stage={}", message.getTaskId(), stage, e);
        }
    }

    @Override
    public void completed(RunnerTaskMessage message, String metricsJson) {
        statusProducer.send(RunnerTaskStatusMessage.builder()
                .taskId(message.getTaskId())
                .dispatchId(message.getDispatchId())
                .status("SUCCESS")
                .currentStage("COMPLETED")
                .progress(100)
                .logMessage("任务完成，压测指标已采集")
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
                .logMessage("任务失败：" + error.getMessage())
                .errorCode(error.getClass().getSimpleName())
                .errorMessage(error.getMessage())
                .occurredAtEpochMs(System.currentTimeMillis())
                .build());
    }

    private int stageProgress(String stage) {
        return switch (stage) {
            case "BUILDING" -> 15;
            case "RUNNING" -> 30;
            case "WAITING_HEALTH" -> 45;
            case "BENCHMARKING" -> 75;
            case "COLLECTING" -> 90;
            case "CLEANING" -> 95;
            default -> 0;
        };
    }

    private String stageMessage(String stage) {
        return switch (stage) {
            case "BUILDING" -> "开始构建 SUT：编译代码并构建 Docker 镜像";
            case "RUNNING" -> "启动实验网络和 SUT 容器";
            case "WAITING_HEALTH" -> "等待 SUT 的 /actuator/health 检查通过";
            case "BENCHMARKING" -> "启动 k6 执行正式压测请求";
            case "COLLECTING" -> "读取压测结果并采集容器资源指标";
            case "CLEANING" -> "清理 SUT、k6 和实验网络资源";
            default -> "进入任务阶段：" + stage;
        };
    }
}
