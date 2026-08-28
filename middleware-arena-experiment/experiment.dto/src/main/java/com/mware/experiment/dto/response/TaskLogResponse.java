package com.mware.experiment.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 任务级 Runner 执行日志，供任务详情页实时查看。 */
@Data
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class TaskLogResponse {

    private Long occurredAtEpochMs;

    private String level;

    private String stage;

    private String message;
}
