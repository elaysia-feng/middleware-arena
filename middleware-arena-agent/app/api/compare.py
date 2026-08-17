"""Agent 优化前后对比 HTTP 入口。

1. 接收 CompareRequest，只负责 HTTP 参数校验。
2. 后续加载两次 experiment_result 与代码版本，判断优化是否有效。
3. 成功时返回 CompareResponse；具体比较逻辑放 graph/service。

TODO[核心逻辑-由你实现]:
- [ ] 加载两次实验 metrics。
- [ ] 计算 QPS/P95/Error/CPU/Memory 变化。
- [ ] 交给 compare_result 节点生成最终 verdict。
"""

from fastapi import APIRouter, HTTPException, status

from app.core.config import get_settings
from app.schemas.api.analysis import CompareRequest, CompareResponse

router = APIRouter(prefix=get_settings().agent_http_prefix, tags=["agent"])


@router.post("/compare", response_model=CompareResponse, response_model_by_alias=True)
async def compare(request: CompareRequest) -> CompareResponse:
    raise HTTPException(
        status_code=status.HTTP_501_NOT_IMPLEMENTED,
        detail=(
            "TODO[Agent Core]: 实现实验结果对比 "
            f"beforeTaskId={request.before_task_id}, afterTaskId={request.after_task_id}"
        ),
    )
