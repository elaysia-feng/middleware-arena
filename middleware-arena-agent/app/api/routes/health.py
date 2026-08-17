"""Health-check routes."""

from fastapi import APIRouter

from app.core.config import get_settings

router = APIRouter(tags=["health"])


@router.get("/health")
def health() -> dict[str, str | bool]:
    settings = get_settings()
    return {
        "status": "ok",
        "service": "middleware-arena-agent",
        "mqEnabled": settings.agent_mq_enabled,
    }
