package com.mware.experiment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mware.experiment.domain.ExperimentPatch;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent Patch Mapper。
 *
 * 状态流转（PROPOSED → ACCEPTED / REJECTED → APPLIED）见 ExperimentPatchServiceImpl。
 */
@Mapper
public interface ExperimentPatchMapper extends BaseMapper<ExperimentPatch> {
}
