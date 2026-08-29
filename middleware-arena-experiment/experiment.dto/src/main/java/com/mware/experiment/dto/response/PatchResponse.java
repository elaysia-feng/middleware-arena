package com.mware.experiment.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Agent 候选补丁响应（状态机：PROPOSED → ACCEPTED/REJECTED → APPLIED）。 */
@Data
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PatchResponse {

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
