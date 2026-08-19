"""固定实验上下文加载说明。

这里不再定义给 LLM 自主调用的 Tool。

原因：task/result/baseline/config/code diff 等数据几乎每次分析都会使用，
应该由 LangGraph ``load_context`` Node 通过 ``ExperimentClient`` 一次性并发加载，
再写入 AnalysisState；如果把这些都做成 Tool，会增加模型轮次、Token 和延迟。

真正给 Agent 自主选择的 Tool 统一定义在 ``analysis_tools.py``：
- get_redis_diagnostics
- get_mysql_diagnostics
- get_rabbitmq_diagnostics
- search_similar_experiments
- search_knowledge_base

TODO:
- [ ] 在 load_context Node 接入 ExperimentClient 的 task/result/baseline/config/diff 方法。
- [ ] 对大日志先程序化聚合，只把摘要和代表性证据写入 State。
"""
