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
 * Agent 分析实体，对应 experiment_analysis 表。
 *
 * <p>1. 数据库所有权属于 experiment-service，Python Agent 不直接操作本表。</p>
 * <p>2. MQ/HTTP 只传 DTO/Message；最终分析状态和结果由 Java 落库。</p>
 * <p>3. JSON 字段第一版直接 String 保存，后续业务层决定如何序列化/反序列化。</p>
 */
@Data
@TableName("experiment_analysis")
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExperimentAnalysis {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;
    private Long userId;
    private Long versionId;
    private Long baselineTaskId;
    private String middlewareType;
    private String analysisType;
    private String triggerType;
    private String status;
    private String currentStage;
    private Integer progress;

    private String bottleneckType;
    private Double confidence;
    private String evidenceJson;
    private String hypothesesJson;
    private String suggestionsJson;
    private String report;

    private String modelName;
    private String promptVersionsJson;
    private String langfuseTraceId;
    private String dispatchId;

    private String errorCode;
    private String errorMessage;

    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
