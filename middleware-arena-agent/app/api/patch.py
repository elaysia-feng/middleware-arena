"""Agent Patch HTTP 入口。

1. 接收 analysisId，后续读取对应诊断结果。
2. 只生成候选 Patch，不直接修改 experiment_version。
3. 用户确认后才调用 experiment-service createVersion。

TODO[核心逻辑-由你实现]:
- [ ] 根据 analysisId 加载 evidence/hypothesis。
- [ ] 调 generate_patch / validate_patch 节点。
- [ ] 设计 Human-in-the-loop 接受/拒绝接口。
"""

from fastapi import APIRouter, HTTPException, status

from app.schemas.analysis import PatchRequest

router = APIRouter(prefix="/agent", tags=["agent"])


@router.post("/patch")
async def generate_patch(request: PatchRequest) -> dict:
    raise HTTPException(
        status_code=status.HTTP_501_NOT_IMPLEMENTED,
        detail=f"TODO[Agent Core]: 实现 analysisId={request.analysis_id} 的 Patch 生成流程",
    )
