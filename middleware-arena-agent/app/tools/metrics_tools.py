"""指标与日志 Tool 占位。

1. 获取当前实验与 baseline 的结构化指标。
2. 获取 Runner / SUT / 中间件必要日志与运行信息。
3. 将原始数据裁剪为 Agent 可消费的证据，避免把超大日志直接塞给 LLM。

TODO:
- [ ] 实现 get_metrics / get_baseline_metrics。
- [ ] 实现 get_runtime_logs，并限制时间窗与最大行数。
- [ ] 后续接入相似实验检索。
"""
