package com.mware.experiment.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Agent 加载实验上下文的内部响应。
 * <p>
 * 固定事实由 experiment-service 一次性组装，避免 Agent 或 LLM 自行猜测任务、版本和指标。
 */
@Data
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class AgentAnalysisContextResponse {

    private Long taskId;
    private Long userId;
    private Long versionId;
    private Long baselineTaskId;
    private String middlewareType;
    private Map<String, Object> config;
    private List<Map<String, Object>> files;
    private List<FileDiff> codeDiff;
    private Map<String, Object> metrics;
    private Map<String, Object> baselineMetrics;
    private List<String> logs;
}
