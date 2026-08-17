"""优化前后对比接口占位。

1. 读取 before / after 两次实验结果。
2. 计算 QPS、P95、错误率、CPU、内存变化。
3. 调用 Compare Agent 判断优化是否有效并生成结论。

TODO:
- [ ] 定义 CompareRequest / CompareResponse。
- [ ] 接入 compare_result 节点。
- [ ] 支持关联原始 analysis_id / patch_id。
"""
