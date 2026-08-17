"""Agent 服务健康检查 Router。

健康检查应该尽量轻量，不执行 LLM、LangGraph、数据库查询等重逻辑，
否则真正业务服务变慢时连探活接口也会一起超时。

当前同时保留：
- ``/health``：容器/Kubernetes/本机可直接探活。
- ``/agent/health``：通过 Gateway 也能访问的统一业务前缀路径。
"""

from fastapi import APIRouter

from app.core.config import get_settings

router = APIRouter(tags=["health"])


def _health_payload() -> dict[str, str | bool]:
    """构造健康检查响应。

    这里的 ``mqEnabled`` 只表示配置上是否启用 MQ Consumer，
    还不是 RabbitMQ 深度连接健康检查；后续如需要可另加 readiness endpoint。
    """
    settings = get_settings()
    return {
        "status": "ok",
        "service": "middleware-arena-agent",
        "mqEnabled": settings.agent_mq_enabled,
    }


# 两个装饰器绑定到同一个函数，因此两条 URL 返回完全相同的数据。
@router.get("/health")
@router.get(f"{get_settings().agent_http_prefix}/health")
def health() -> dict[str, str | bool]:
    """返回 Agent 进程的基础存活状态。"""
    return _health_payload()
