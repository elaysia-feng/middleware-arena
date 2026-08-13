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
 * 实验任务实体：只回答"某个用户运行某个版本，现在跑到哪了"。
 * <p>
 * 状态只在这里保存一份（runner 侧不持久化，回传 progress/result 由本表承载）；
 * name / description 不重复存，模板 / 版本里已有。
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

    /** 任务状态：CREATED / QUEUED / RUNNING / SUCCESS / FAILED / CANCELLED */
    private String status;

    /** 当前阶段：BUILDING / STARTING / BENCHMARKING / COLLECTING / ANALYZING */
    private String currentStage;

    /** 进度 0~100 */
    private Integer progress;

    /** 本次入队时的会员等级快照，排队期间不随 VIP 到期变化。 */
    private String tierSnapshot;

    /** 每次创建或重试都会刷新，用于忽略上一轮迟到的状态消息。 */
    private String dispatchId;

    /** 机器可识别的失败码，例如 RESOURCE_BUSY。 */
    private String errorCode;

    /** 失败原因（status=FAILED 时有值） */
    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private LocalDateTime updatedAt;
}
