"""Agent analyze HTTP 入口。

1. Gateway 转发 AGENT_HTTP_PREFIX/analyze 到这里。
2. 请求只携带 taskId/baselineTaskId，转换为统一 AnalysisCommand。
3. 真正分析只调用 analysis_service.run_analysis，不在 API 层写 LangGraph 逻辑。
"""

from fastapi import APIRouter, HTTPException, status

from app.core.config import get_settings
from app.schemas.analysis import AnalyzeRequest, AnalyzeResponse
from app.services.analysis_service import AnalysisCommand, run_analysis

router = APIRouter(prefix=get_settings().agent_http_prefix, tags=["agent"])


@router.post("/analyze", response_model=AnalyzeResponse, response_model_by_alias=True)
async def analyze(request: AnalyzeRequest) -> AnalyzeResponse:
    command = AnalysisCommand(
        task_id=request.task_id,
        baseline_task_id=request.baseline_task_id,
        trigger_type="MANUAL",
    )
    try:
        result = await run_analysis(command)
    except NotImplementedError as exc:
        raise HTTPException(status_code=status.HTTP_501_NOT_IMPLEMENTED, detail=str(exc)) from exc

    return AnalyzeResponse(
        task_id=result.task_id,
        analysis_id=result.analysis_id,
        status=result.status,
        result=result.data,
        trace_id=result.trace_id,
    )
