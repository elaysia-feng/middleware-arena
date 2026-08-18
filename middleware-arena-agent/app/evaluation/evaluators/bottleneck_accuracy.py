"""瓶颈判断 Evaluator 占位。

1. 对比 Agent bottleneck 与 Dataset 中的期望瓶颈。
2. 输出可写回 Langfuse 的 score / reason。
3. 支持严格匹配与语义 Judge 两种评测方式。

TODO:
- [ ] 定义 evaluator 输入输出。
- [ ] 接入 Langfuse Dataset / Experiment。
- [ ] 增加 evidence 是否支持该结论的联合评分。
"""
