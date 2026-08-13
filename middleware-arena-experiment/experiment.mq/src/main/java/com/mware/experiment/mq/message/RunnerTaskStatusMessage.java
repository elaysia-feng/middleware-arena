package com.mware.experiment.mq.message;

import lombok.Data;

/** Runner 回传的任务状态事件。 */
@Data
public class RunnerTaskStatusMessage {
    private Long taskId;
    private String dispatchId;
    private String status;
    private String currentStage;
    private String metricsJson;
    private String errorCode;
    private String errorMessage;
    private Long occurredAtEpochMs;
}
