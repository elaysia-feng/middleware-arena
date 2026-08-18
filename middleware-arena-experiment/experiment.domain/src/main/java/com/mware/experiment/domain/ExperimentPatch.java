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
 * Agent 候选代码 Patch 实体，对应 experiment_patch 表。
 *
 * <p>1. Agent 只负责生成候选 Patch，最终是否应用由用户确认。</p>
 * <p>2. accepted/applied 后由 experiment-service 创建新的 experiment_version。</p>
 * <p>3. filesPatchJson / validationJson 第一版直接保存 JSON 字符串。</p>
 */
@Data
@TableName("experiment_patch")
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExperimentPatch {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long analysisId;
    private Long sourceVersionId;
    private String status;
    private String summary;
    private String filesPatchJson;
    private String validationJson;
    private Long appliedVersionId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
