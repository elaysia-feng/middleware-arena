package com.mware.experiment.biz.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mware.common.web.ApiException;
import com.mware.common.web.ErrorCode;
import com.mware.common.web.UserContext;
import com.mware.experiment.biz.ExperimentPatchService;
import com.mware.experiment.domain.ExperimentAnalysis;
import com.mware.experiment.domain.ExperimentPatch;
import com.mware.experiment.dto.response.PatchResponse;
import com.mware.experiment.mapper.ExperimentAnalysisMapper;
import com.mware.experiment.mapper.ExperimentPatchMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 补丁状态机实现：迁移规则只在本类，全部用条件更新实现乐观锁。
 *
 * <p>归属校验：patch → analysis 链路拿到 userId，仅 analysis 属主可读 / 评审 / 应用。</p>
 */
@Service
@RequiredArgsConstructor
public class ExperimentPatchServiceImpl implements ExperimentPatchService {

    private final ExperimentPatchMapper experimentPatchMapper;
    private final ExperimentAnalysisMapper experimentAnalysisMapper;

    @Override
    public PatchResponse get(Long patchId) {
        ExperimentPatch patch = experimentPatchMapper.selectById(patchId);
        if (patch == null) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        requireAnalysisOwner(patch.getAnalysisId());
        return toPatchResponse(patch);
    }

    @Override
    public List<PatchResponse> listByAnalysis(Long analysisId) {
        requireAnalysisOwner(analysisId);
        return experimentPatchMapper.selectList(new LambdaQueryWrapper<ExperimentPatch>()
                .eq(ExperimentPatch::getAnalysisId, analysisId)
                .orderByAsc(ExperimentPatch::getId))
                .stream().map(this::toPatchResponse).toList();
    }

    private PatchResponse toPatchResponse(ExperimentPatch patch) {
        return PatchResponse.builder()
                .id(patch.getId())
                .analysisId(patch.getAnalysisId())
                .sourceVersionId(patch.getSourceVersionId())
                .status(patch.getStatus())
                .summary(patch.getSummary())
                .filesPatchJson(patch.getFilesPatchJson())
                .validationJson(patch.getValidationJson())
                .appliedVersionId(patch.getAppliedVersionId())
                .createdAt(patch.getCreatedAt())
                .updatedAt(patch.getUpdatedAt())
                .build();
    }

    @Override
    public void review(Long patchId, String decision) {
        // 1. 目标状态只允许 ACCEPTED / REJECTED
        if (!ExperimentPatch.STATUS_ACCEPTED.equals(decision)
                && !ExperimentPatch.STATUS_REJECTED.equals(decision)) {
            throw new ApiException(ErrorCode.PARAM_INVALID);
        }
        ExperimentPatch patch = experimentPatchMapper.selectById(patchId);
        if (patch == null) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        requireAnalysisOwner(patch.getAnalysisId());

        // 2. 乐观锁迁移：PROPOSED → ACCEPTED / REJECTED；
        //    行数为 0 说明补丁已被评审过或状态被并发修改
        int updated = experimentPatchMapper.update(null, new LambdaUpdateWrapper<ExperimentPatch>()
                .eq(ExperimentPatch::getId, patchId)
                .eq(ExperimentPatch::getStatus, ExperimentPatch.STATUS_PROPOSED)
                .set(ExperimentPatch::getStatus, decision)
                .set(ExperimentPatch::getUpdatedAt, LocalDateTime.now()));
        if (updated != 1) {
            throw new ApiException(409, "补丁已评审或状态已变化，当前不可评审");
        }
    }

    @Override
    public void apply(Long patchId, Long appliedVersionId) {
        if (appliedVersionId == null) {
            throw new ApiException(ErrorCode.PARAM_INVALID);
        }
        ExperimentPatch patch = experimentPatchMapper.selectById(patchId);
        if (patch == null) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        requireAnalysisOwner(patch.getAnalysisId());

        // ACCEPTED → APPLIED：乐观锁迁移 + 登记应用产生的新版本 id；
        // 新版本文件（filesPatchJson 应用产物）由调用方生成并落 experiment_version
        int updated = experimentPatchMapper.update(null, new LambdaUpdateWrapper<ExperimentPatch>()
                .eq(ExperimentPatch::getId, patchId)
                .eq(ExperimentPatch::getStatus, ExperimentPatch.STATUS_ACCEPTED)
                .set(ExperimentPatch::getStatus, ExperimentPatch.STATUS_APPLIED)
                .set(ExperimentPatch::getAppliedVersionId, appliedVersionId)
                .set(ExperimentPatch::getUpdatedAt, LocalDateTime.now()));
        if (updated != 1) {
            throw new ApiException(409, "补丁未处于 ACCEPTED 状态，不能应用");
        }
    }

    /** patch → analysis 归属校验：仅 analysis 属主可操作 */
    private void requireAnalysisOwner(Long analysisId) {
        ExperimentAnalysis analysis = experimentAnalysisMapper.selectById(analysisId);
        if (analysis == null) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        if (!analysis.getUserId().equals(UserContext.getUserId())) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
    }
}
