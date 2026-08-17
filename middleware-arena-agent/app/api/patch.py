"""Agent Patch HTTP 入口。

1. 接收 PatchRequest，只负责 HTTP 参数校验。
2. 后续调用 Patch Service / LangGraph 节点生成候选 Patch。
3. 成功时返回 PatchResponse；不直接修改 experiment_version。

TODO[核心逻辑-由你实现]:
- [ ] 根据 analysisId 加载 evidence/hypothesis。
- [ ] 调 generate_patch / validate_patch 节点。
- [ ] 设计 Human-in-the-loop 接受/拒绝接口。
"""

from fastapi import APIRouter, HTTPException, status

from app.core.config import get_settings
from app.schemas.api.analysis import PatchRequest

router = APIRouter(prefix=get_settings().agent_http_prefix, tags=["agent"])


@router.post("/patch")
async def generate_patch(request: PatchRequest) -> dict:
    raise HTTPException(
        status_code=status.HTTP_501_NOT_IMPLEMENTED,
        detail=f"TODO[Agent Core]: 实现 analysisId={request.analysis_id} 的 Patch 生成流程",
    )
