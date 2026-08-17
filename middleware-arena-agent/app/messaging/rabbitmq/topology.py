"""RabbitMQ topology names shared with Java AgentRabbitConfig."""

EXCHANGE_ANALYSIS = "agent.analysis.exchange"
QUEUE_ANALYSIS = "agent.analysis.queue"
ROUTING_KEY_ANALYSIS = "agent.analysis"

DLX_ANALYSIS = "agent.analysis.dlx"
DLQ_ANALYSIS = "agent.analysis.dlq"
ROUTING_KEY_DEAD = "dead"

EXCHANGE_STATUS = "agent.analysis.status.exchange"
QUEUE_STATUS = "agent.analysis.status.queue"
ROUTING_KEY_STATUS = "agent.analysis.status"

# TODO: 增加 Java/Python 契约测试，防止拓扑字符串或 queue arguments 漂移。
