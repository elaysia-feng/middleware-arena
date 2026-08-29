package com.mware.experiment.dto.request;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 补丁应用请求：appliedVersionId 为补丁文件应用后生成的新 experiment_version.id。 */
@Data
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PatchApplyRequest {

    private Long appliedVersionId;
}
