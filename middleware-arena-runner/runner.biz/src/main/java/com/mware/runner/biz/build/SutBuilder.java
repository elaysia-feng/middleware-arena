package com.mware.runner.biz.build;

import com.mware.runner.biz.config.ExperimentType;
import com.mware.runner.dto.RunnerTaskMessage;

/** 构建 candidate SUT 镜像，或返回 baseline 预构建镜像。 */
@FunctionalInterface
public interface SutBuilder {

    /**
     * 为任务准备可运行的 SUT 镜像。
     *
     * @return Docker 镜像 tag
     */
    String build(RunnerTaskMessage message, ExperimentType type);
}
