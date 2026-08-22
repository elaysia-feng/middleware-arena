package com.mware.experiment.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/** Agent 相似实验检索结果。 */
@Data
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SimilarExperimentResponse {

    private Long taskId;
    private Long versionId;
    private String middlewareType;
    private String scenario;
    private Double similarityScore;
    private Map<String, Object> metrics;
}
