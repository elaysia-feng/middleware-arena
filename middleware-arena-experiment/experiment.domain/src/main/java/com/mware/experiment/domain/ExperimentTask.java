package com.mware.experiment.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 实验任务实体（骨架占位，字段与 sql/init.sql 对齐）。
 * <p>
 * - status 状态机：pending / queued / running / success / failed / cancelled
 * - versionId 关联实验版本快照表
 */
@Data
@TableName("experiment_task")
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExperimentTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 创建人用户 ID */
    private Long userId;

    /** 实验版本快照 ID */
    private Long versionId;

    /** 实验任务名称 */
    private String name;

    /** 任务描述 */
    private String description;

    /** 任务状态：pending / queued / running / success / failed / cancelled */
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
