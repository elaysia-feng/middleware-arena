from contextlib import asynccontextmanager
import logging

from fastapi import FastAPI

from app.api.analyze import router as analyze_router
from app.api.compare import router as compare_router
from app.api.patch import router as patch_router
from app.core.config import get_settings
from app.mq.connection import rabbitmq_manager
from app.mq.consumer import agent_analysis_consumer
from app.resource_advice import (
    ResourceAdviceRequest,
    ResourceAdviceResponse,
    calculate_resource_advice,
)

logger = logging.getLogger(__name__)
settings = get_settings()


@asynccontextmanager
async def lifespan(_: FastAPI):
    """管理 Agent 基础设施生命周期；核心 LangGraph 不在这里初始化业务状态。"""
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


app = FastAPI(title="Middleware Arena AI Agent", version="0.2.0", lifespan=lifespan)
app.include_router(analyze_router)
app.include_router(patch_router)
app.include_router(compare_router)


def _health_payload() -> dict[str, str | bool]:
    return {
        "status": "ok",
        "service": "middleware-arena-agent",
        "mqEnabled": settings.agent_mq_enabled,
    }


@app.get("/health")
@app.get(f"{settings.agent_http_prefix}/health")
def health() -> dict[str, str | bool]:
    """保留直连健康检查，并提供 Gateway 可访问的 /agent/health。"""
    return _health_payload()


@app.post("/resource/advice", response_model=ResourceAdviceResponse)
@app.post(f"{settings.agent_http_prefix}/resource/advice", response_model=ResourceAdviceResponse)
def resource_advice(request: ResourceAdviceRequest) -> ResourceAdviceResponse:
    """兼容旧路径，同时允许通过 Gateway 的 /agent/** 访问资源建议。"""
    return calculate_resource_advice(request)
