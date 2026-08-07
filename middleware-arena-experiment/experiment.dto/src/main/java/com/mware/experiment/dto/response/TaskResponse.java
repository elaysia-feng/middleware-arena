package com.mware.experiment.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 实验任务响应对象（后端输出，纯 DTO）。
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

    private String name;

    private String description;

    /** 任务状态：pending / queued / running / success / failed / cancelled */
    private String status;

    private LocalDateTime createdAt;
}
