"""FastAPI application entrypoint."""

from contextlib import asynccontextmanager
import logging

from fastapi import FastAPI

from app.api.router import api_router
from app.core.config import get_settings
from app.messaging.rabbitmq.connection import rabbitmq_manager
from app.messaging.rabbitmq.consumer import agent_analysis_consumer

logger = logging.getLogger(__name__)
settings = get_settings()


@asynccontextmanager
async def lifespan(_: FastAPI):
    """Start and stop shared infrastructure resources."""
    if settings.agent_mq_enabled:
        await rabbitmq_manager.connect()
        await agent_analysis_consumer.start()
    else:
        logger.info("Agent MQ consumer disabled by AGENT_MQ_ENABLED=false")

    try:
        yield
    finally:
        if settings.agent_mq_enabled:
            await agent_analysis_consumer.stop()
            await rabbitmq_manager.close()


app = FastAPI(
    title="Middleware Arena AI Agent",
    version="0.3.0",
    lifespan=lifespan,
)
app.include_router(api_router)
