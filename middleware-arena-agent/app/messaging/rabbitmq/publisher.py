"""RabbitMQ status/result publisher."""

import aio_pika

from app.messaging.rabbitmq.connection import RabbitMQManager, rabbitmq_manager
from app.messaging.rabbitmq.topology import ROUTING_KEY_STATUS
from app.schemas.messages import AgentAnalysisStatusMessage


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


# TODO: resultJson 过大时改为 OSS object key，并增加 publish 失败补偿指标。
