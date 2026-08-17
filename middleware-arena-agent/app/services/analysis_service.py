"""HTTP / MQ 共用的 Agent 分析业务入口。

1. HTTP 的 POST /agent/analyze 与 MQ consumer 都只负责把输入转换成 AnalysisCommand。
2. 本 service 负责真正启动 LangGraph、写 Langfuse Trace、控制 max_iterations 和结果结构化。
3. 不在 API 或 consumer 中复制 Agent 业务逻辑，避免两条入口行为不一致。

TODO:
- [ ] 定义 AnalysisCommand：task_id / analysis_id? / baseline_task_id / trigger_type / user_context。
- [ ] 调 graph.builder 构建的 compiled graph，并统一注入 Langfuse callback。
- [ ] load_context 通过 experiment internal HTTP API 获取 version / metrics / diff / logs。
- [ ] 返回 AnalysisResult，供 HTTP 直接响应或 MQ publisher 序列化回传。
"""
