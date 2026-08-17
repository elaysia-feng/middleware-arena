"""Langfuse 可观测与评测客户端占位。

1. 初始化 Langfuse，并为一次 Agent 分析创建 trace 上下文。
2. 记录各 LangGraph 节点、LLM 调用、Tool 调用的输入输出与耗时。
3. 为 Prompt version、模型、task_id、version_id 等写入 metadata。

TODO:
- [ ] 从环境变量读取 Langfuse 配置。
- [ ] 接入 LangGraph/LangChain callback handler。
- [ ] 统一 trace/span 命名规范。
- [ ] 失败时降级，不能阻塞核心 Agent 流程。
"""
