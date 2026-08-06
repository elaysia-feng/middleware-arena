# middleware-arena-experiment

实验服务：实验模板 / 版本快照 / 任务状态机 / SSE 进度推送。

## TODO

- [ ] 实验模板管理（CRUD）
- [ ] 版本快照（保存 / 回滚实验配置版本）
- [ ] 实验任务创建（调用 runner 服务）
- [ ] 任务状态机：pending → queued → running → success / failed / cancelled
- [ ] SSE 实时进度推送
- [ ] 接入数据源与 experiment.mapper
- [ ] 接入 API 网关路由
