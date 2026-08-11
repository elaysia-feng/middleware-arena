package com.mware.runner.biz.benchmark;

import com.mware.runner.biz.config.ExperimentType;
import com.mware.runner.dto.RunnerTaskMessage;

/**
 * benchmark()：按 runParamsJson 生成 k6 脚本（smoke → warmup → 正式阶梯），
 * 起一次性 k6 容器（--rm，跑完自删）在实验网络内压 SUT，输出 summary.json 供 collectMetrics 解析。
 * <p>
 * 档位（第一版从轻）：smoke 5VU×10s → warmup 10VU×20s → 正式 20VU×30s；
 * VIP 再开放 50/100/200 VU、更长 duration。阈值（thresholds）用 P95 / 错误率判断实验是否达标。
 */
public interface K6Runner {

    /**
     * 执行压测（临时 k6 容器，跑完自删）。
     *
     * @param baseUrl SUT 容器内直连地址（如 http://ma-task-1001-product:8080）
     */
    void run(RunnerTaskMessage message, ExperimentType type, String baseUrl);
}
