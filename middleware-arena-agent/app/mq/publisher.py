"""Agent 分析状态/结果发布端占位。

1. 将 ANALYZING / SUCCESS / FAILED 状态发送到 agent.analysis.status.exchange。
2. JSON 字段按 AgentAnalysisStatusMessage 的 camelCase alias 输出，保证 Java Jackson 可直接反序列化。
3. Publisher Confirm/mandatory return 语义需要与 Java 生产端同等级处理，避免 Agent 已完成但 experiment-service 永远不知道。

TODO:
- [ ] 使用 aio-pika persistent Message(delivery_mode=PERSISTENT)。
- [ ] publish(..., mandatory=True) 并处理不可路由异常。
- [ ] resultJson 先控制体积；后续报告过大时改为对象存储引用，不把大正文长期塞 MQ。
- [ ] 发布成功后 consumer 才允许 ACK 原 analysis task。
"""
