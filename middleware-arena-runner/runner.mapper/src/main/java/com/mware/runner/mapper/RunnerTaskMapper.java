package com.mware.runner.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mware.runner.domain.RunnerTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * Runner 压测任务 Mapper（骨架占位）。
 * <p>
 * TODO：接入数据源时在 application.yml 启用数据源后生效。
 */
@Mapper
public interface RunnerTaskMapper extends BaseMapper<RunnerTask> {
}
