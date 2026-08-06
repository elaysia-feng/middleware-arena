package com.mware.experiment.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实验任务实体（骨架占位）。
 * <p>
 * TODO：
 *   - 字段与 sql/init.sql 同步
 *   - status 状态机：pending / queued / running / success / failed / cancelled
 *   - versionId 关联实验版本快照表
 */
@Data
@TableName("experiment_task")
public class ExperimentTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long versionId;

    private String status;

    private LocalDateTime createdAt;
}
