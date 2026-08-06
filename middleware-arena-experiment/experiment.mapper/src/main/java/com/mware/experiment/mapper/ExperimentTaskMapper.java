package com.mware.experiment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mware.experiment.domain.ExperimentTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 实验任务 Mapper（骨架占位）。
 * <p>
 * TODO：接入数据源时把 experiment.mapper 依赖引入 experiment.biz，
 * 并在 application.yml 启用数据源后生效。
 */
@Mapper
public interface ExperimentTaskMapper extends BaseMapper<ExperimentTask> {
}
