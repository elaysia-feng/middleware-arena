"""Health-check routes."""

from fastapi import APIRouter

from app.core.config import get_settings

router = APIRouter(tags=["health"])


def _health_payload() -> dict[str, str | bool]:
    settings = get_settings()
    return {
        "status": "ok",
        "service": "middleware-arena-agent",
        "mqEnabled": settings.agent_mq_enabled,
    }


@router.get("/health")
@router.get(f"{get_settings().agent_http_prefix}/health")
def health() -> dict[str, str | bool]:
    """Direct health check plus Gateway-accessible health check."""
    return _health_payload()
