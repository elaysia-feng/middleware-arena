"""资源建议 HTTP Router。

Router 只负责 HTTP 入参/出参；真正的预算计算在 ``services/resource_advice.py``。
因为这个计算当前没有异步 I/O，所以这里使用普通 ``def`` 即可，FastAPI 会按同步端点处理。
"""

from fastapi import APIRouter

from app.core.config import get_settings
from app.schemas.resource import ResourceAdviceRequest, ResourceAdviceResponse
from app.services.resource_advice import calculate_resource_advice

# 和分析接口共用 /agent 前缀，因此完整路径为 /agent/resource/advice。
router = APIRouter(
    prefix=get_settings().agent_http_prefix,
    tags=["resource"],
)


@router.post(
    "/resource/advice",
    response_model=ResourceAdviceResponse,
)
def resource_advice(request: ResourceAdviceRequest) -> ResourceAdviceResponse:
    """根据规则预算、历史样本和 LLM 辅助建议计算最终容器资源预算。"""

    # Request 已由 FastAPI + Pydantic 校验，这里直接交给业务 Service。
    return calculate_resource_advice(request)
