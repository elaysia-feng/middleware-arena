"""指标分析节点占位。

1. 对比当前实验与 baseline 的 QPS、P95、错误率、CPU、内存。
2. 识别明显异常并生成 metric_findings。
3. 为后续中间件专家 SubGraph 提供结构化证据，不直接下最终结论。

TODO:
- [ ] 计算相对变化率和阈值告警。
- [ ] 接入 Langfuse Prompt: middleware-metrics-analysis。
- [ ] 输出结构化 metric_findings。
"""
