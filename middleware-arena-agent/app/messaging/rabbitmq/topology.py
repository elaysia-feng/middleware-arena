"""RabbitMQ 拓扑名称常量。

这些字符串属于 Java/Python 跨语言契约，必须与 Java ``AgentRabbitConfig`` 完全一致。
RabbitMQ 中“同名 Queue/Exchange”如果声明参数不同，不会自动覆盖，而会直接报
``PRECONDITION_FAILED``，因此不要在 Consumer/Publisher 中散落硬编码字符串。

整体流向：

experiment-service
    -> EXCHANGE_ANALYSIS
    -> ROUTING_KEY_ANALYSIS
    -> QUEUE_ANALYSIS
    -> Python Agent

Agent 处理失败且 reject(requeue=False)
    -> DLX_ANALYSIS
    -> ROUTING_KEY_DEAD
    -> DLQ_ANALYSIS

Python Agent 状态回传
    -> EXCHANGE_STATUS
    -> ROUTING_KEY_STATUS
    -> QUEUE_STATUS
    -> experiment-service
"""

# ----------------------------------------------------------------------
# Java -> Python：自动分析任务
# ----------------------------------------------------------------------
# Direct Exchange：按照 routing key 精确路由到分析队列。
EXCHANGE_ANALYSIS = "agent.analysis.exchange"
# Python Agent 实际消费的任务队列。
QUEUE_ANALYSIS = "agent.analysis.queue"
# Experiment Producer 发送自动分析任务时使用的 routing key。
ROUTING_KEY_ANALYSIS = "agent.analysis"

# ----------------------------------------------------------------------
# 分析失败后的死信链路
# ----------------------------------------------------------------------
# Analysis Queue 配置的 Dead Letter Exchange。
DLX_ANALYSIS = "agent.analysis.dlx"
# 重试耗尽、消息格式错误等最终进入的死信队列，便于人工排查/补偿。
DLQ_ANALYSIS = "agent.analysis.dlq"
# Analysis Queue reject 后转发到 DLX 时使用的 routing key。
ROUTING_KEY_DEAD = "dead"

# ----------------------------------------------------------------------
# Python -> Java：分析进度/结果回传
# ----------------------------------------------------------------------
# Agent 发布 ANALYZING/SUCCESS/FAILED 状态的 Exchange。
EXCHANGE_STATUS = "agent.analysis.status.exchange"
# Java experiment-service 消费分析状态的队列。
QUEUE_STATUS = "agent.analysis.status.queue"
# 状态消息的 routing key。
ROUTING_KEY_STATUS = "agent.analysis.status"


# TODO:
# 1. 增加 Java/Python MQ 契约测试，自动校验这些名称。
# 2. 契约测试还要检查 exchange type、durable、queue arguments/DLX，而不只是字符串。
