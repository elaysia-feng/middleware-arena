package com.mware.experiment.controller;

import com.mware.common.web.ApiException;
import com.mware.common.web.ApiResponse;
import com.mware.common.web.ErrorCode;
import com.mware.experiment.biz.ExperimentService;
import com.mware.experiment.biz.impl.AgentAnalysisLeaseService;
import com.mware.experiment.dto.response.AgentAnalysisContextResponse;
import com.mware.experiment.dto.response.SimilarExperimentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Agent 调用的实验内部接口，不对前端直接开放。 */
@RestController
@RequestMapping("/experiment/internal/agent")
public class AgentInternalController {

    private final ExperimentService experimentService;
    private final AgentAnalysisLeaseService agentAnalysisLeaseService;
    private final String internalToken;

    /**
     * 注入实验查询、租约服务和内部凭据。
     * @param experimentService 实验业务接口
     * @param agentAnalysisLeaseService 分析租约服务
     * @param internalToken 内部调用凭据
     */
    public AgentInternalController(
            ExperimentService experimentService,
            AgentAnalysisLeaseService agentAnalysisLeaseService,
            @Value("${ma.internal-token:middleware-arena-internal-token}") String internalToken) {
        this.experimentService = experimentService;
        this.agentAnalysisLeaseService = agentAnalysisLeaseService;
        this.internalToken = internalToken;
    }

    /**
     * 分析租约抢占：Python 消费者收到 MQ 消息后先调本接口，抢到才开始分析并 ack 消息。
     * 当前批次 CREATED / QUEUED → ANALYZING 条件更新保证重投消息只有一个能抢到；
     * 分析中每次状态回传都会刷新 updatedAt（续约），超时未续约由 Java 定时回收重派。
     * @param analysisId 分析 ID
     * @param taskId 实验任务 ID
     * @param dispatchId 消息投递批次
     * @param requestToken 内部调用凭据
     * @return 是否成功抢占当前批次
     */
    @PostMapping("/claim/{analysisId}")
    public ApiResponse<Boolean> claimAnalysis(
            @PathVariable("analysisId") Long analysisId,
            @RequestParam("taskId") Long taskId,
            @RequestParam("dispatchId") String dispatchId,
            @RequestHeader("X-Internal-Token") String requestToken) {
        verifyInternalToken(requestToken);
        return ApiResponse.ok(agentAnalysisLeaseService.claim(analysisId, taskId, dispatchId));
    }

    /**
     * 查询分析需要的实验上下文。
     * @param taskId 任务 ID
     * @param baselineTaskId 可选基线任务 ID
     * @param requestToken 内部调用凭据
     * @return 分析上下文
     */
    @GetMapping("/context/{taskId}")
    public ApiResponse<AgentAnalysisContextResponse> getAnalysisContext(
            @PathVariable("taskId") Long taskId,
            @RequestParam(value = "baselineTaskId", required = false) Long baselineTaskId,
            @RequestHeader("X-Internal-Token") String requestToken) {
        verifyInternalToken(requestToken);
        return ApiResponse.ok(experimentService.getAgentAnalysisContext(taskId, baselineTaskId));
    }

    /**
     * 查询可供分析参考的相似实验。
     * @param taskId 当前任务 ID
     * @param limit 返回数量上限
     * @param requestToken 内部调用凭据
     * @return 相似实验列表
     */
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
