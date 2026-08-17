"""Agent 分析状态/结果 Publisher。

Consumer 负责接收 Java -> Python 的分析任务；本文件负责 Python -> Java 回传：
``ANALYZING / SUCCESS / FAILED``。

Publisher 只关心“怎么可靠发消息”，不负责决定什么时候成功、什么时候失败。
"""

import aio_pika

from app.messaging.rabbitmq.connection import RabbitMQManager, rabbitmq_manager
from app.messaging.rabbitmq.topology import ROUTING_KEY_STATUS
from app.schemas.messages import AgentAnalysisStatusMessage


async def publish_status(
    message: AgentAnalysisStatusMessage,
    manager: RabbitMQManager = rabbitmq_manager,
) -> None:
    """把一条 Agent 状态消息发布到 status exchange。

    ``message`` 已经经过 Pydantic 校验，所以这里不再检查业务字段。
    ``manager`` 可注入，便于单元测试替换成 fake/mock RabbitMQ Manager。
    """

    # 正常情况下 lifespan 已连接；这里做兜底，保证 Publisher 独立调用也可用。
    if manager.status_exchange is None:
        await manager.connect()
    if manager.status_exchange is None:
        raise RuntimeError("Agent status exchange 未初始化")

    # by_alias=True：把 Python snake_case 转为 Java DTO 对应的 camelCase。
    # 例如 analysis_id -> analysisId。
    # exclude_none=True：未设置的可选字段不发送，减少消息体并避免无意义 null。
    body = message.model_dump_json(
        by_alias=True,
        exclude_none=True,
    ).encode("utf-8")

    rabbit_message = aio_pika.Message(
        body=body,
        content_type="application/json",
        # PERSISTENT + durable queue/exchange：Broker 重启时消息可靠性更高。
        delivery_mode=aio_pika.DeliveryMode.PERSISTENT,
        # message_id 使用 analysisId，便于 RabbitMQ 日志/追踪定位分析任务。
        message_id=str(message.analysis_id),
        # correlation_id 使用 taskId，方便把 status 与原 experiment_task 关联。
        correlation_id=str(message.task_id),
    )

    # manager 的 Channel 开启了 publisher_confirms=True；
    # await publish 返回后才能认为 Broker 已接收这条状态消息。
    await manager.status_exchange.publish(
        rabbit_message,
        routing_key=ROUTING_KEY_STATUS,
        # mandatory=True：如果消息找不到任何可路由 Queue，不要静默丢掉。
        mandatory=True,
    )


# TODO:
# 1. resultJson 过大时改成 OSS object key，MQ 只传轻量引用。
# 2. 增加 publish 失败次数、耗时等监控指标。
# 3. 如业务要求更高可靠性，可增加 outbox/补偿表，处理 Broker 长时间不可用场景。
