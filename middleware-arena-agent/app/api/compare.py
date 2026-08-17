"""Agent 优化前后对比 HTTP 入口。

1. 接收 beforeTaskId / afterTaskId。
2. 后续加载两次 experiment_result 与代码版本，判断优化是否有效。
3. API 层只保留参数校验，具体比较逻辑放 graph/service。

TODO[核心逻辑-由你实现]:
- [ ] 加载两次实验 metrics。
- [ ] 计算 QPS/P95/Error/CPU/Memory 变化。
- [ ] 交给 compare_result 节点生成最终 verdict。
"""

from fastapi import APIRouter, HTTPException, status

from app.schemas.analysis import CompareRequest

router = APIRouter(prefix="/agent", tags=["agent"])


@router.post("/compare")
async def compare(request: CompareRequest) -> dict:
    raise HTTPException(
        status_code=status.HTTP_501_NOT_IMPLEMENTED,
        detail=(
            "TODO[Agent Core]: 实现实验结果对比 "
            f"beforeTaskId={request.before_task_id}, afterTaskId={request.after_task_id}"
        ),
    )
