"""RabbitMQ 长连接、Channel 与拓扑声明。

这个模块只负责 RabbitMQ 基础设施，不处理任何 Agent 业务。

几个容易混淆的概念：
- Connection：Python 进程到 RabbitMQ Server 的 TCP/AMQP 长连接。
- Channel：复用在 Connection 上的逻辑通道，发布/消费通常都通过 Channel 完成。
- Exchange：生产者先把消息发到 Exchange。
- Routing Key：Exchange 根据它决定消息路由到哪个 Queue。
- Queue：消费者真正从 Queue 取消息。

FastAPI 生命周期启动时只建立一次连接，后续所有消息复用，避免每条消息都重新 TCP 握手。
"""

import logging

import aio_pika
from aio_pika import ExchangeType

from app.core.config import Settings, get_settings
from app.messaging.rabbitmq.topology import (
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
    """维护 Agent 进程共享的 RabbitMQ 连接与拓扑对象。"""

    def __init__(self, settings: Settings | None = None) -> None:
        # 支持测试时注入自定义 RabbitMQ 地址；生产默认取全局 Settings。
        self.settings = settings or get_settings()

        # 下面几个字段在 connect() 前都为 None；connect 后保存 aio-pika 对象引用。
        self.connection = None
        self.channel = None
        self.analysis_queue = None
        self.analysis_exchange = None
        self.status_exchange = None

    async def connect(self) -> None:
        """建立 robust connection、Channel，并声明所需 Exchange/Queue。

        ``connect_robust`` 相比普通 connect 会处理连接意外断开后的自动恢复，
        更适合常驻 FastAPI 服务。
        """

        # 幂等保护：已经连接时直接复用，不重复建立连接。
        if self.connection and not self.connection.is_closed:
            return

        self.connection = await aio_pika.connect_robust(
            host=self.settings.rabbit_host,
            port=self.settings.rabbit_port,
            login=self.settings.rabbit_user,
            password=self.settings.rabbit_password,
            virtualhost=self.settings.rabbit_vhost,
        )

        # publisher_confirms=True：发布消息后等待 Broker Confirm。
        # 这样 publish() 成功返回时，至少可以确认 RabbitMQ Broker 已接收到消息，
        # 比“发出去就不管”的 fire-and-forget 更可靠。
        self.channel = await self.connection.channel(publisher_confirms=True)

        # prefetch 控制“Consumer 最多同时拿多少条还没 ACK 的消息”。
        # 当前设为 1，意味着一条 Agent 分析没结束前不会继续压入大量新任务，
        # 能防止 LLM/CPU/内存被瞬时并发打满。
        await self.channel.set_qos(prefetch_count=self.settings.agent_mq_prefetch)

        # 声明 Exchange/Queue/Binding。Java/Python 两端参数必须保持一致。
        await self._declare_topology()

        logger.info(
            "RabbitMQ connected: %s:%s",
            self.settings.rabbit_host,
            self.settings.rabbit_port,
        )

    async def _declare_topology(self) -> None:
        """声明 Agent 所需 RabbitMQ 拓扑。

        ``durable=True`` 表示 RabbitMQ 重启后 Exchange/Queue 定义仍然存在。
        注意 durable Queue 里想让消息也尽量保留，Publisher 还需要发送 PERSISTENT 消息。
        """
        if self.channel is None:
            raise RuntimeError("RabbitMQ channel 尚未建立")

        # Java -> Python 的分析任务 Exchange。
        self.analysis_exchange = await self.channel.declare_exchange(
            EXCHANGE_ANALYSIS,
            ExchangeType.DIRECT,
            durable=True,
        )

        # Python -> Java 的分析状态 Exchange。
        self.status_exchange = await self.channel.declare_exchange(
            EXCHANGE_STATUS,
            ExchangeType.DIRECT,
            durable=True,
        )

        # 分析失败后转发到这里，再进入 DLQ。
        dlx = await self.channel.declare_exchange(
            DLX_ANALYSIS,
            ExchangeType.DIRECT,
            durable=True,
        )

        # Agent 主分析队列。
        self.analysis_queue = await self.channel.declare_queue(
            QUEUE_ANALYSIS,
            durable=True,
            arguments={
                # 当 Consumer reject(requeue=False) 时，RabbitMQ 自动把消息投给这个 DLX。
                "x-dead-letter-exchange": DLX_ANALYSIS,
                "x-dead-letter-routing-key": ROUTING_KEY_DEAD,
            },
        )
        await self.analysis_queue.bind(
            self.analysis_exchange,
            ROUTING_KEY_ANALYSIS,
        )

        # 死信队列：最终失败的分析任务留在这里，不会无限重试。
        dlq = await self.channel.declare_queue(
            DLQ_ANALYSIS,
            durable=True,
        )
        await dlq.bind(dlx, ROUTING_KEY_DEAD)

        # Java 端消费 Agent 状态的队列。
        status_queue = await self.channel.declare_queue(
            QUEUE_STATUS,
            durable=True,
        )
        await status_queue.bind(
            self.status_exchange,
            ROUTING_KEY_STATUS,
        )

    async def close(self) -> None:
        """释放 Channel/Connection，并清空本地引用。

        这里只关闭客户端连接，不会删除 durable Queue/Exchange。
        """
        if self.channel and not self.channel.is_closed:
            await self.channel.close()
        if self.connection and not self.connection.is_closed:
            await self.connection.close()

        # 清空引用，防止后续代码误以为这些对象仍然可用。
        self.channel = None
        self.connection = None
        self.analysis_queue = None
        self.analysis_exchange = None
        self.status_exchange = None


# FastAPI 整个进程共享同一个 Manager，由 lifespan 统一 connect/close。
rabbitmq_manager = RabbitMQManager()


# TODO:
# 1. 增加 RabbitMQ 健康检查指标与重连告警。
# 2. 增加 Java/Python 拓扑契约测试。
# 3. 后续高并发时评估是否需要拆消费 Channel 和发布 Channel。
