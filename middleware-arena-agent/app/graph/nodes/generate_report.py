"""分析报告生成节点占位。

1. 汇总指标变化、瓶颈、证据、建议和候选 Patch。
2. 生成适合前端展示和社区发布的结构化报告。
3. 报告必须保留 trace_id / prompt_version / model 等可追溯信息。

TODO:
- [ ] 定义 Report schema。
- [ ] 接入 Langfuse Prompt: middleware-report-generator。
- [ ] 输出摘要、证据链、风险、优化建议和验证建议。
"""
