"""固定指标 / 日志预处理说明。

这里不再定义给 LLM 自主调用的 Tool。

基础 metrics、baseline、resource、日志摘要属于每次分析都需要的 Context：
1. Node 直接加载原始数据；
2. Python 代码计算 QPS/P95/ErrorRate 等变化率；
3. 日志先做聚合、去重、裁剪；
4. 最后把结构化摘要写入 AnalysisState。

只有 Agent 判断需要继续深挖时，才调用 ``analysis_tools.py`` 中的 Redis/MySQL/RabbitMQ
诊断 Tool 或历史实验/知识库检索 Tool。

TODO:
- [ ] 实现 metrics/baseline 变化率计算。
- [ ] 实现日志错误类型聚合与代表性样本裁剪。
- [ ] 为 load_context Node 增加并发加载和超时降级。
"""
