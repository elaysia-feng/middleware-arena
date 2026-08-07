package com.mware.runner.biz;

import com.mware.runner.domain.RunnerTask;

/**
 * Runner 业务接口。
 * <p>
 * 方法签名已定，具体实现留待接入 MQ / docker / k6 后补齐。
 */
public interface RunnerService {

    /** 从消息队列 / experiment-service 拉取待执行任务 */
    RunnerTask pullTask();

    /** 根据中间件类型（Nginx / Redis / Kafka / Envoy 等）构建镜像 / 二进制 */
    RunnerTask build(RunnerTask task);

    /** 启动 Docker 容器（给定版本 + 配置） */
    RunnerTask run(RunnerTask task);

    /** 执行 k6 压测（HTTP / gRPC / TCP） */
    RunnerTask benchmark(RunnerTask task);

    /** 采集指标（CPU / 内存 / 延迟 / QPS） */
    RunnerTask collectMetrics(RunnerTask task);

    /** 清理：停止容器、删除临时镜像、释放端口 */
    void cleanup(RunnerTask task);

    /** 完整流水线编排：pull → build → run → bench → collect → cleanup */
    RunnerTask execute(RunnerTask task);

    /** 按上游实验任务 ID（task_id）查询任务状态 */
    RunnerTask getTask(String taskId);
}
