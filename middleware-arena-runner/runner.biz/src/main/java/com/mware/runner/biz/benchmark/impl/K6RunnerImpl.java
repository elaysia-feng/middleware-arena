package com.mware.runner.biz.benchmark.impl;

import com.mware.runner.biz.benchmark.K6Runner;
import com.mware.runner.biz.config.ExperimentType;
import com.mware.runner.biz.config.RunnerProperties;
import com.mware.runner.biz.docker.DockerService;
import com.mware.runner.dto.RunnerTaskMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * k6 压测实现：生成阶梯脚本 → 一次性容器压测 → summary.json 落盘供 collectMetrics 解析。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class K6RunnerImpl implements K6Runner {

    private final RunnerProperties properties;
    private final DockerService dockerService;

    @Override
    public void run(RunnerTaskMessage message, ExperimentType type, String baseUrl) {
        Long taskId = message.getTaskId();
        log.info("开始压测 taskId={}, type={}, target={}{} ({})",
                taskId, type, baseUrl, type.k6Path(), type.httpMethod());

        // TODO[Runner]：完整压测编排
        // 1. 生成 k6 脚本写入 properties.k6.workDir（阶梯参数由 runParamsJson 的
        //    concurrencyLadder / duration 驱动，见 ExperimentVersion.runParamsJson 注释）
        // 2. docker run --rm --network {net}
        //        -v {workDir}:/scripts {images.k6} run /scripts/{taskId}.js
        //        --summary-export /scripts/summary-{taskId}.json
        // 3. 阈值校验：P95 / 错误率超限则实验标记不达标（Grafana k6 thresholds）
        // 4. k6 容器跑完自删，脚本 / summary 落盘供 collectMetrics 读取
    }
}
