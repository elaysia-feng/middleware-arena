package com.mware.experiment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mware.experiment.domain.ExperimentResult;
import org.apache.ibatis.annotations.Mapper;

/**
 * 实验结果 Mapper（结果持久化到 experiment_result 表，task_id 唯一）。
 */
@Mapper
public interface ExperimentResultMapper extends BaseMapper<ExperimentResult> {
}
