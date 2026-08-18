"""FastAPI 应用入口。

这个文件只负责两件事：
1. 创建 FastAPI app 并挂载 Router。
2. 管理 RabbitMQ 这类“整个进程共享”的基础设施生命周期。

不要把 Agent 分析业务、Prompt、LangGraph 节点逻辑直接写到 main.py，
否则后面测试和复用都会很困难。
"""

from contextlib import asynccontextmanager
import logging

from fastapi import FastAPI

from app.api.router import api_router
from app.core.config import get_settings
from app.messaging.rabbitmq.connection import rabbitmq_manager
from app.messaging.rabbitmq.consumer import agent_analysis_consumer

logger = logging.getLogger(__name__)

# Settings 已通过 lru_cache 做成进程级单例，这里拿到的是全局统一配置。
settings = get_settings()


@asynccontextmanager
async def lifespan(_: FastAPI):
    """管理 FastAPI 从启动到关闭期间的共享资源。

    FastAPI 推荐用 lifespan 处理这类资源：
    - ``yield`` 之前：应用启动阶段。
    - ``yield`` 期间：应用正常对外提供服务。
    - ``yield`` 之后：应用关闭阶段。

    RabbitMQ 连接是长连接，所以应该在进程启动时建立、进程关闭时释放，
    而不是每收到一条 HTTP/MQ 请求就重新连接一次。
    """

    if settings.agent_mq_enabled:
        # 1. 建立 RabbitMQ robust connection/channel，并声明 exchange/queue。
        await rabbitmq_manager.connect()
        # 2. 在已经声明好的 analysis queue 上注册异步 Consumer。
        await agent_analysis_consumer.start()
    else:
        # 核心 LangGraph 尚未实现时可以关闭 MQ，只调试 HTTP 接口。
        logger.info("Agent MQ consumer disabled by AGENT_MQ_ENABLED=false")

    try:
        # yield 之后 FastAPI 开始正常接收请求。
        yield
    finally:
        if settings.agent_mq_enabled:
            # 关闭顺序：先停止继续消费消息，再关闭 RabbitMQ 连接。
            await agent_analysis_consumer.stop()
            await rabbitmq_manager.close()


# lifespan 参数把上面的启动/关闭逻辑绑定到 FastAPI 生命周期。
app = FastAPI(
    title="Middleware Arena AI Agent",
    version="0.3.0",
    lifespan=lifespan,
)

# 所有业务 Router 都先汇总到 api/router.py，再由 main.py 只挂载一次。
app.include_router(api_router)
