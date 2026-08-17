"""Agent 分析接口占位。

1. 接收 task_id / version_id 等分析请求参数。
2. 调用 LangGraph 主流程并返回结构化诊断结果。
3. 后续接入 SSE/流式事件，向前端暴露分析阶段。

TODO:
- [ ] 定义 AnalyzeRequest / AnalyzeResponse。
- [ ] 调用 app.graph.builder 中构建好的 graph。
- [ ] 补充异常映射、trace_id 返回与鉴权。
"""
