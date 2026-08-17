"""Agent analyze HTTP 入口。

1. Gateway 转发 AGENT_HTTP_PREFIX/analyze 到这里。
2. HTTP Request 转换为 Service AnalysisCommand。
3. Service AnalysisResult 再转换为 HTTP AnalyzeResponse。
"""

from fastapi import APIRouter, HTTPException, status

from app.core.config import get_settings
from app.schemas.api.analysis import AnalyzeRequest, AnalyzeResponse
from app.schemas.commands.analysis import AnalysisCommand
from app.services.analysis_service import run_analysis

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
        trace_id=result.trace_id,
        result=result.data,
    )
