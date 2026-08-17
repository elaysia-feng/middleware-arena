"""Agent 分析状态/结果发布端。

1. 只接受 AgentAnalysisStatusMessage 这种 MQ Message。
2. 使用 Pydantic alias 输出 Java Jackson 可直接反序列化的 camelCase JSON。
3. 消息持久化，publish await 成功后才允许上游 ACK 原分析任务。

TODO:
- [ ] resultJson 过大后改为 OSS/object-key，不长期通过 MQ 传大报告。
- [ ] 后续增加 publish 失败指标和补偿表。
"""

import aio_pika

from app.mq.connection import RabbitMQManager, rabbitmq_manager
from app.mq.constants import ROUTING_KEY_STATUS
from app.schemas.mq.analysis_status_message import AgentAnalysisStatusMessage


async def publish_status(
    message: AgentAnalysisStatusMessage,
    manager: RabbitMQManager = rabbitmq_manager,
) -> None:
    if manager.status_exchange is None:
        await manager.connect()
    if manager.status_exchange is None:
        raise RuntimeError("Agent status exchange 未初始化")

    body = message.model_dump_json(by_alias=True, exclude_none=True).encode("utf-8")
    rabbit_message = aio_pika.Message(
        body=body,
        content_type="application/json",
        delivery_mode=aio_pika.DeliveryMode.PERSISTENT,
        message_id=str(message.analysis_id),
        correlation_id=str(message.task_id),
    )
    await manager.status_exchange.publish(
        rabbit_message,
        routing_key=ROUTING_KEY_STATUS,
        mandatory=True,
    )
