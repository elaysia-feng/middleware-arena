"""瓶颈裁决节点占位。

1. 对 hypotheses 按证据完整度和置信度排序。
2. 选出主瓶颈与次要瓶颈，拒绝无证据结论。
3. 决定后续进入 Patch 生成还是直接生成报告。

TODO:
- [ ] 定义 BottleneckResult schema。
- [ ] 接入 Langfuse Prompt: middleware-bottleneck-judge。
- [ ] 增加 confidence 下限与 uncertain 状态。
"""
