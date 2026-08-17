"""加载实验上下文节点占位。

1. 根据 task_id/version_id 拉取实验、版本、代码快照与运行参数。
2. 拉取当前指标、基线指标和必要日志。
3. 将外部数据标准化后写入 AnalysisState。

TODO:
- [ ] 调用 experiment_tools/version_tools/metrics_tools。
- [ ] 校验任务状态必须可分析。
- [ ] 处理缺少 baseline/logs 时的降级逻辑。
"""
