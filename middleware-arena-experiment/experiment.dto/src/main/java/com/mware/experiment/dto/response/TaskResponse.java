package com.mware.experiment.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 实验任务响应对象（后端输出，纯 DTO）。
 * <p>
 * 只暴露任务运行状态；name / description 不重复存（模板 / 版本里已有）。
 */
@Data
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class TaskResponse {

    private Long id;

    /** 创建人用户 ID */
    private Long userId;

    /** 实验版本快照 ID */
    private Long versionId;

    /** 任务状态：QUEUED / RUNNING / SUCCESS / FAILED / CANCELLED */
    private String status;

    /** 当前阶段：BUILDING / STARTING / BENCHMARKING / COLLECTING / ANALYZING */
    private String currentStage;

    /** 进度 0~100 */
    private Integer progress;

    /** 失败原因（status=FAILED 时有值） */
    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;
}
