package com.mware.experiment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mware.experiment.domain.ExperimentPatch;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent Patch Mapper。
 *
 * TODO[业务]: ACCEPTED / REJECTED / APPLIED 状态流转放 Service 处理。
 */
@Mapper
public interface ExperimentPatchMapper extends BaseMapper<ExperimentPatch> {
}
