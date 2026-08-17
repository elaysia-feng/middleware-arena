"""LangGraph 主图构建入口占位。

1. 注册通用分析节点与各中间件 SubGraph。
2. 定义节点之间的普通边和条件边。
3. 对外只暴露一个 build_analysis_graph()，供 API 层调用。

TODO:
- [ ] 注册 load_context / analyze_metrics / analyze_code。
- [ ] 接入 middleware_router 条件路由。
- [ ] 注册 hypothesis / bottleneck / patch / report 节点。
- [ ] 增加 Human-in-the-loop 中断点。
- [ ] 接入 Langfuse callback / trace metadata。
"""
