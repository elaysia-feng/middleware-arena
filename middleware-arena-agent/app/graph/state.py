"""LangGraph 全局状态定义占位。

1. 保存一次分析从加载实验到生成报告的共享状态。
2. 区分原始输入、分析中间结果、Patch 与最终报告。
3. 为后续 SubGraph 约定统一字段，避免节点间随意传 dict。

TODO:
- [ ] 迁移并扩展现有 app/state.py 的 AnalysisState。
- [ ] 增加 task_id/version_id/template_id/middleware_type。
- [ ] 增加 files/code_diff/logs/metric_findings/code_findings。
- [ ] 增加 hypotheses/evidence/patches/iteration/max_iterations。
- [ ] 明确可累加字段所需 reducer。
"""
