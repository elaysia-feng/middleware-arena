package com.mware.runner.biz;

/**
 * Runner 业务接口。
 * <p>
 * TODO：
 *   - 任务拉取：从消息队列（RabbitMQ / Redis Stream）或 experiment-service 拉取任务
 *   - 构建流水线：根据中间件类型（Nginx / Redis / Kafka / Envoy 等）生成 Dockerfile 并构建
 *   - 容器管理：通过 docker-java 或 docker-compose 启动 / 停止容器
 *   - k6 压测：生成 k6 脚本 → 执行 → 解析结果 JSON
 *   - 指标采集：通过 docker stats / cAdvisor / Prometheus 采集 CPU/内存/延迟/QPS
 *   - 清理：停止容器、删除临时镜像、释放端口
 *   - 流水线编排：按序执行各阶段，支持断点续跑与失败回滚
 */
public interface RunnerService {

}
