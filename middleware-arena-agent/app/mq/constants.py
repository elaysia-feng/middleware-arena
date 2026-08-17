"""Agent RabbitMQ 拓扑常量。

1. 与 Java 端 AgentRabbitConfig 保持完全一致：exchange / queue / routing-key / DLX 名称不能漂移。
2. Python 消费端和 Java 生产端都使用 durable direct exchange + durable queue。
3. 重试耗尽后统一 reject(requeue=False)，由 x-dead-letter-exchange 进入 DLQ。

TODO:
- [ ] Python consumer 声明拓扑时复用本文件常量，禁止散落字符串。
- [ ] Java AgentRabbitConfig 修改拓扑时同步修改本文件，并补契约测试。
- [ ] 后续如要做 VIP/FREE Agent 队列，再在两端同时扩展，不单边新增。
"""

EXCHANGE_ANALYSIS = "agent.analysis.exchange"
QUEUE_ANALYSIS = "agent.analysis.queue"
ROUTING_KEY_ANALYSIS = "agent.analysis"

DLX_ANALYSIS = "agent.analysis.dlx"
DLQ_ANALYSIS = "agent.analysis.dlq"
ROUTING_KEY_DEAD = "dead"

EXCHANGE_STATUS = "agent.analysis.status.exchange"
QUEUE_STATUS = "agent.analysis.status.queue"
ROUTING_KEY_STATUS = "agent.analysis.status"
