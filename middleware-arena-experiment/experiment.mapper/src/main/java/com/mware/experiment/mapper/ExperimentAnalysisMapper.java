package com.mware.experiment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mware.experiment.domain.ExperimentAnalysis;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 分析 Mapper。
 *
 * TODO[业务]: 状态迁移、幂等条件更新等规则放 Service，不在 Mapper 中写业务判断。
 */
@Mapper
public interface ExperimentAnalysisMapper extends BaseMapper<ExperimentAnalysis> {
}
