package com.mware.experiment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mware.experiment.domain.ExperimentAnalysis;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 分析 Mapper。
 *
 * 状态迁移、幂等条件更新等规则在 Service（RunnerTaskStatusConsumer / ExperimentPatchServiceImpl）。
 */
@Mapper
public interface ExperimentAnalysisMapper extends BaseMapper<ExperimentAnalysis> {
}
