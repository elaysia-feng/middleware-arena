# middleware-arena-runner

Runner 服务：拉取任务 → 构建 → 起容器 → k6 压测 → 采指标 → 清理流水线。

## TODO

- [ ] 任务拉取（从消息队列或 experiment-service）
- [ ] 构建流水线（Dockerfile 生成 + 构建）
- [ ] 容器管理（docker-java / docker-compose）
- [ ] k6 压测（脚本生成 → 执行 → 结果解析）
- [ ] 指标采集（CPU / 内存 / 延迟 / QPS）
- [ ] 清理流水线（停止容器、删除临时资源）
- [ ] 流水线编排（断点续跑 + 失败回滚）
- [ ] 接入 API 网关路由
