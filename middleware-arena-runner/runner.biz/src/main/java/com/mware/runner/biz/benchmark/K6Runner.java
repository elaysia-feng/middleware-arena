package com.mware.runner.biz.benchmark;

import com.mware.runner.biz.config.RunnerProperties;
import com.mware.runner.biz.docker.DockerService;
import com.mware.runner.biz.docker.ExperimentType;
import com.mware.runner.dto.RunnerTaskMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * benchmark()：按 runParamsJson 生成 k6 脚本（smoke → warmup → 正式阶梯），
 * 起一次性 k6 容器（--rm，跑完自删）在实验网络内压 SUT，输出 summary.json 供 collectMetrics 解析。
 * <p>
 * 档位（第一版从轻）：smoke 5VU×10s → warmup 10VU×20s → 正式 20VU×30s；
 * VIP 再开放 50/100/200 VU、更长 duration。阈值（thresholds）用 P95 / 错误率判断实验是否达标。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class K6Runner {

    private final RunnerProperties properties;
    private final DockerService dockerService;

    /**
     * 执行压测（临时 k6 容器，跑完自删）。
     *
     * @param baseUrl SUT 容器内直连地址（如 http://ma-task-1001-product:8080）
     */
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
