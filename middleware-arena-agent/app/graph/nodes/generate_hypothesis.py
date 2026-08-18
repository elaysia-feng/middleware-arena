"""性能瓶颈假设生成节点占位。

1. 汇总 metric_findings、code_findings 和中间件专家分析。
2. 生成一个或多个候选 hypotheses，每个假设必须绑定 evidence。
3. 给出 confidence，但不直接生成最终报告。

TODO:
- [ ] 定义 Hypothesis schema。
- [ ] 接入 Langfuse Prompt: middleware-hypothesis-generator。
- [ ] 限制候选数量并去重同义假设。
"""
