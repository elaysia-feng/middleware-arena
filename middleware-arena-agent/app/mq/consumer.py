"""Agent 自动分析任务消费者占位。

1. 消费 agent.analysis.queue，解析 AgentAnalysisTaskMessage，并转成统一 AnalysisCommand。
2. manual ack：LangGraph 成功且结果可靠回传后 ACK；异常时最多本地重试 3 次。
3. 3 次仍失败则 reject(requeue=False)，由队列 DLX 参数送入 agent.analysis.dlq；进程崩溃导致未 ACK 时由 RabbitMQ 自动重新投递。

TODO:
- [ ] 使用 aio-pika IncomingMessage.process(ignore_processed=True) 或显式 ack/reject。
- [ ] MAX_ATTEMPTS=3，initial interval=1s，语义对齐 Java Runner 当前 retry 配置。
- [ ] 调 app.services.analysis_service.run_analysis，HTTP 与 MQ 必须共用同一业务入口。
- [ ] 成功/失败都通过 publisher 回传 AgentAnalysisStatusMessage。
- [ ] analysisId / dispatchId 幂等检查完成前不要执行 LLM。
"""
