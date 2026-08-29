package com.mware.experiment.biz;

import com.mware.experiment.domain.ExperimentPatch;
import com.mware.experiment.dto.response.PatchResponse;

import java.util.List;

/**
 * Agent 候选补丁的状态机服务。
 *
 * <p>生命周期：PROPOSED →（用户评审）→ ACCEPTED / REJECTED →（应用并产生新版本）→ APPLIED。</p>
 * <p>状态迁移全部用条件更新（WHERE status = 期望前态）实现乐观锁，
 * 迁移规则只在本服务，Mapper 不写业务判断。</p>
 */
public interface ExperimentPatchService {

    /** 查询单个补丁（含归属校验：仅 analysis 属主可读） */
    PatchResponse get(Long patchId);

    /** 查询某次分析产生的全部补丁（含归属校验） */
    List<PatchResponse> listByAnalysis(Long analysisId);

    /**
     * 用户评审：PROPOSED → ACCEPTED / REJECTED。
     *
     * @param decision 只允许 ACCEPTED / REJECTED
     */
    void review(Long patchId, String decision);

    /**
     * 应用补丁：ACCEPTED → APPLIED，并登记 patch 应用后的新版本 id。
     * 版本文件由调用方（Agent HTTP 流程）基于 filesPatchJson 生成，
     * 本方法只负责终态与版本号的原子登记。
     */
    void apply(Long patchId, Long appliedVersionId);
}
