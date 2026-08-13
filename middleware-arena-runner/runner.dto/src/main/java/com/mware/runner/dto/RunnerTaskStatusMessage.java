package com.mware.runner.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Runner 回传给 experiment-service 的任务状态事件。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
