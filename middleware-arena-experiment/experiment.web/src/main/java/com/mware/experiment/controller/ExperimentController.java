package com.mware.experiment.controller;

import com.mware.common.web.ApiException;
import com.mware.common.web.ApiResponse;
import com.mware.common.web.ErrorCode;
import com.mware.common.web.UserContext;
import com.mware.experiment.biz.ExperimentPatchService;
import com.mware.experiment.biz.ExperimentService;
import com.mware.experiment.dto.request.CreateVersionRequest;
import com.mware.experiment.dto.request.CreateTemplateRequest;
import com.mware.experiment.dto.request.PatchApplyRequest;
import com.mware.experiment.dto.request.PatchReviewRequest;
import com.mware.experiment.dto.request.UpdateTemplateRequest;
import com.mware.experiment.dto.response.TaskResponse;
import com.mware.experiment.dto.response.TaskLogResponse;
import com.mware.experiment.dto.response.TemplateResponse;
import com.mware.experiment.dto.response.VersionDiffResponse;
import com.mware.experiment.dto.response.VersionResponse;
import com.mware.experiment.dto.response.PatchResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 实验与模板管理服务入口。
 * <p>
 * 本服务统一管理模板元数据、版本快照和实验任务；
 * {@code middleware-arena-templates} 只是内置模板资产目录，不另建重复的模板服务。
 * Controller 只负责 HTTP 参数和身份校验，具体权限、版本与任务状态逻辑由 {@link ExperimentService} 完成。
 */
@Tag(name = "实验")
@RestController
@RequestMapping("/experiment")
public class ExperimentController {

    private final ExperimentService experimentService;
    private final ExperimentPatchService experimentPatchService;

    public ExperimentController(ExperimentService experimentService,
            ExperimentPatchService experimentPatchService) {
        this.experimentService = experimentService;
        this.experimentPatchService = experimentPatchService;
    }

    @Operation(summary = "健康检查")
    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        return ApiResponse.ok("pong");
    }

    @Operation(summary = "创建实验模板")
    @PostMapping("/template")
    public ApiResponse<TemplateResponse> createTemplate(@RequestBody CreateTemplateRequest request) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.ok(experimentService.createTemplate(request, userId));
    }

    @Operation(summary = "从内置资产创建实验模板")
    @PostMapping("/template/builtin/{builtinKey}")
    public ApiResponse<TemplateResponse> createBuiltinTemplate(@PathVariable("builtinKey") String builtinKey) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.ok(experimentService.createBuiltinTemplate(builtinKey, userId));
    }

    @Operation(summary = "更新实验模板")
    @PutMapping("/template/{templateId}")
    public ApiResponse<TemplateResponse> updateTemplate(@PathVariable("templateId") Long templateId,
            @RequestBody UpdateTemplateRequest request) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.ok(experimentService.updateTemplate(templateId, request, userId));
    }

    @Operation(summary = "删除实验模板")
    @DeleteMapping("/template/{templateId}")
    public ApiResponse<Void> deleteTemplate(@PathVariable("templateId") Long templateId) {
        experimentService.deleteTemplate(templateId);
        return ApiResponse.ok();
    }

    @Operation(summary = "查询实验模板详情")
    @GetMapping("/template/{templateId}")
    public ApiResponse<TemplateResponse> getTemplate(@PathVariable("templateId") Long templateId) {
        return ApiResponse.ok(experimentService.getTemplate(templateId));
    }

    @Operation(summary = "分页查询实验模板")
    @GetMapping("/template/page")
    public ApiResponse<List<TemplateResponse>> pageTemplates(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return ApiResponse.ok(experimentService.pageTemplates(page, size));
    }

    @Operation(summary = "为模板保存新版本（文件快照 + 运行参数）")
    @PostMapping("/version")
    public ApiResponse<VersionResponse> createVersion(@RequestBody CreateVersionRequest request) {
        return ApiResponse.ok(experimentService.createVersion(
                request.getTemplateId(),
                request.getFilesJson(),
                request.getRunParamsJson(),
                request.getChangeSummary()));
    }

    @Operation(summary = "回滚实验版本")
    @PostMapping("/version/rollback")
    public ApiResponse<Void> rollbackVersion(
            @RequestParam("templateId") Long templateId,
            @RequestParam("versionId") Long versionId) {
        experimentService.rollbackVersion(templateId, versionId);
        return ApiResponse.ok();
    }

    @Operation(summary = "查询模板所有版本（按版本号倒序）")
    @GetMapping("/version/list")
    public ApiResponse<List<VersionResponse>> listVersions(@RequestParam("templateId") Long templateId) {
        return ApiResponse.ok(experimentService.listVersions(templateId));
    }

    @Operation(summary = "查询某个版本详情")
    @GetMapping("/version/{versionId}")
    public ApiResponse<VersionResponse> getVersion(@PathVariable("versionId") Long versionId) {
        return ApiResponse.ok(experimentService.getVersion(versionId));
    }

    @Operation(summary = "对比两个版本（文件级差异）")
    @GetMapping("/version/diff")
    public ApiResponse<VersionDiffResponse> diffVersion(
            @RequestParam("fromVersionId") Long fromVersionId,
            @RequestParam("toVersionId") Long toVersionId) throws JsonProcessingException {
        return ApiResponse.ok(experimentService.diffVersion(fromVersionId, toVersionId));
    }

    @Operation(summary = "创建实验任务（拉起 runner 压测）")
    @PostMapping("/task")
    public ApiResponse<TaskResponse> createTask(@RequestParam("versionId") Long versionId) {
        return ApiResponse.ok(experimentService.createTask(versionId));
    }

    @Operation(summary = "查询实验任务详情")
    @GetMapping("/task/{taskId}")
    public ApiResponse<TaskResponse> getTask(@PathVariable("taskId") Long taskId) {
        return ApiResponse.ok(experimentService.getTask(taskId));
    }

    @Operation(summary = "分页查询实验任务")
    @GetMapping("/task/page")
    public ApiResponse<List<TaskResponse>> pageTasks(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return ApiResponse.ok(experimentService.pageTasks(page, size));
    }

    @Operation(summary = "取消实验任务")
    @PostMapping("/task/{taskId}/cancel")
    public ApiResponse<Void> cancelTask(@PathVariable("taskId") Long taskId) {
        experimentService.cancelTask(taskId);
        return ApiResponse.ok();
    }

    @Operation(summary = "重试失败任务")
    @PostMapping("/task/{taskId}/retry")
    public ApiResponse<TaskResponse> retryTask(@PathVariable("taskId") Long taskId) {
        return ApiResponse.ok(experimentService.retryTask(taskId));
    }

    @Operation(summary = "查询任务进度（SSE 轮询）")
    @GetMapping("/task/{taskId}/progress")
    public ApiResponse<String> getTaskProgress(@PathVariable("taskId") Long taskId) {
        return ApiResponse.ok(experimentService.getTaskProgress(taskId));
    }

    @Operation(summary = "查询任务执行日志")
    @GetMapping("/task/{taskId}/logs")
    public ApiResponse<List<TaskLogResponse>> getTaskLogs(
            @PathVariable("taskId") Long taskId,
            @RequestParam(value = "limit", defaultValue = "200") int limit) {
        return ApiResponse.ok(experimentService.getTaskLogs(taskId, limit));
    }

    @Operation(summary = "查询补丁")
    @GetMapping("/patch/{patchId}")
    public ApiResponse<PatchResponse> getPatch(@PathVariable("patchId") Long patchId) {
        return ApiResponse.ok(experimentPatchService.get(patchId));
    }

    @Operation(summary = "查询某次分析产生的全部补丁")
    @GetMapping("/analysis/{analysisId}/patch")
    public ApiResponse<List<PatchResponse>> listPatches(@PathVariable("analysisId") Long analysisId) {
        return ApiResponse.ok(experimentPatchService.listByAnalysis(analysisId));
    }

    @Operation(summary = "评审补丁（ACCEPTED / REJECTED）")
    @PostMapping("/patch/{patchId}/review")
    public ApiResponse<Void> reviewPatch(
            @PathVariable("patchId") Long patchId,
            @RequestBody PatchReviewRequest request) {
        experimentPatchService.review(patchId, request.getDecision());
        return ApiResponse.ok();
    }

    @Operation(summary = "应用补丁（登记应用后产生的新版本）")
    @PostMapping("/patch/{patchId}/apply")
    public ApiResponse<Void> applyPatch(
            @PathVariable("patchId") Long patchId,
            @RequestBody PatchApplyRequest request) {
        experimentPatchService.apply(patchId, request.getAppliedVersionId());
        return ApiResponse.ok();
    }
}
