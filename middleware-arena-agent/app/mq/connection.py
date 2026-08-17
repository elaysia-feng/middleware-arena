"""RabbitMQ 长连接与拓扑声明。

1. 读取与 Java 端一致的 RabbitMQ 连接配置。
2. 声明 Agent analysis/status/DLX 拓扑，所有名称来自 constants.py。
3. FastAPI lifespan 复用一条 robust connection/channel，关闭时统一释放。

TODO:
- [ ] 后续增加连接健康指标与重连告警。
- [ ] Java/Python 增加拓扑契约测试，防止 durable/arguments 漂移。
"""

import logging

import aio_pika
from aio_pika import ExchangeType

from app.core.config import Settings, get_settings
from app.mq.constants import (
    DLQ_ANALYSIS,
    DLX_ANALYSIS,
    EXCHANGE_ANALYSIS,
    EXCHANGE_STATUS,
    QUEUE_ANALYSIS,
    QUEUE_STATUS,
    ROUTING_KEY_ANALYSIS,
    ROUTING_KEY_DEAD,
    ROUTING_KEY_STATUS,
)

logger = logging.getLogger(__name__)


class RabbitMQManager:
    def __init__(self, settings: Settings | None = None) -> None:
        self.settings = settings or get_settings()
        self.connection = None
        self.channel = None
        self.analysis_queue = None
        self.analysis_exchange = None
        self.status_exchange = None

    async def connect(self) -> None:
        if self.connection and not self.connection.is_closed:
            return

        self.connection = await aio_pika.connect_robust(
            host=self.settings.rabbit_host,
            port=self.settings.rabbit_port,
            login=self.settings.rabbit_user,
            password=self.settings.rabbit_password,
            virtualhost=self.settings.rabbit_vhost,
        )
        self.channel = await self.connection.channel(publisher_confirms=True)
        await self.channel.set_qos(prefetch_count=self.settings.agent_mq_prefetch)
        await self._declare_topology()
        logger.info("RabbitMQ connected: %s:%s", self.settings.rabbit_host, self.settings.rabbit_port)

    async def _declare_topology(self) -> None:
        if self.channel is None:
            raise RuntimeError("RabbitMQ channel 尚未建立")

        self.analysis_exchange = await self.channel.declare_exchange(
            EXCHANGE_ANALYSIS,
            ExchangeType.DIRECT,
            durable=True,
        )
        self.status_exchange = await self.channel.declare_exchange(
            EXCHANGE_STATUS,
            ExchangeType.DIRECT,
            durable=True,
        )
        dlx = await self.channel.declare_exchange(
            DLX_ANALYSIS,
            ExchangeType.DIRECT,
            durable=True,
        )

        self.analysis_queue = await self.channel.declare_queue(
            QUEUE_ANALYSIS,
            durable=True,
            arguments={
                "x-dead-letter-exchange": DLX_ANALYSIS,
                "x-dead-letter-routing-key": ROUTING_KEY_DEAD,
            },
        )
        await self.analysis_queue.bind(self.analysis_exchange, ROUTING_KEY_ANALYSIS)

        dlq = await self.channel.declare_queue(DLQ_ANALYSIS, durable=True)
        await dlq.bind(dlx, ROUTING_KEY_DEAD)

        status_queue = await self.channel.declare_queue(QUEUE_STATUS, durable=True)
        await status_queue.bind(self.status_exchange, ROUTING_KEY_STATUS)

    async def close(self) -> None:
        if self.channel and not self.channel.is_closed:
            await self.channel.close()
        if self.connection and not self.connection.is_closed:
            await self.connection.close()
        self.channel = None
        self.connection = None
        self.analysis_queue = None
        self.analysis_exchange = None
        self.status_exchange = None


rabbitmq_manager = RabbitMQManager()
