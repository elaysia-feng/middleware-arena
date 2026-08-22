package com.mware.experiment.controller;

import com.mware.common.web.ApiException;
import com.mware.common.web.ApiResponse;
import com.mware.common.web.ErrorCode;
import com.mware.experiment.biz.ExperimentService;
import com.mware.experiment.dto.response.AgentAnalysisContextResponse;
import com.mware.experiment.dto.response.SimilarExperimentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Agent 调用的实验只读内部接口，不对前端直接开放。 */
@RestController
@RequestMapping("/experiment/internal/agent")
public class AgentInternalController {

    private final ExperimentService experimentService;
    private final String internalToken;

    public AgentInternalController(
            ExperimentService experimentService,
            @Value("${ma.internal-token:middleware-arena-internal-token}") String internalToken) {
        this.experimentService = experimentService;
        this.internalToken = internalToken;
    }

    @GetMapping("/context/{taskId}")
    public ApiResponse<AgentAnalysisContextResponse> getAnalysisContext(
            @PathVariable("taskId") Long taskId,
            @RequestParam(value = "baselineTaskId", required = false) Long baselineTaskId,
            @RequestHeader("X-Internal-Token") String requestToken) {
        verifyInternalToken(requestToken);
        return ApiResponse.ok(experimentService.getAgentAnalysisContext(taskId, baselineTaskId));
    }

    @GetMapping("/similar/{taskId}")
    public ApiResponse<List<SimilarExperimentResponse>> findSimilarExperiments(
            @PathVariable("taskId") Long taskId,
            @RequestParam(value = "limit", defaultValue = "5") int limit,
            @RequestHeader("X-Internal-Token") String requestToken) {
        verifyInternalToken(requestToken);
        return ApiResponse.ok(experimentService.findSimilarExperiments(taskId, limit));
    }

    private void verifyInternalToken(String requestToken) {
        if (!internalToken.equals(requestToken)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
    }
}
