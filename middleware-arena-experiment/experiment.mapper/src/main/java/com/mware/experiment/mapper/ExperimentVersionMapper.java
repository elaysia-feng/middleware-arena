package com.mware.experiment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mware.experiment.domain.ExperimentVersion;
import org.apache.ibatis.annotations.Mapper;

/**
 * 实验版本 Mapper（骨架占位）。
 * <p>
 * TODO：接入数据源时在 application.yml 启用数据源后生效；
 * 配合 rollbackVersion 按版本快照回滚模板配置。
 */
@Mapper
public interface ExperimentVersionMapper extends BaseMapper<ExperimentVersion> {
}
