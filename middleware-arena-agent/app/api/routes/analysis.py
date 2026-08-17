"""Analysis HTTP routes."""

from fastapi import APIRouter, HTTPException, status

from app.schemas.analysis import (
    AnalyzeRequest,
    AnalyzeResponse,
    CompareRequest,
    CompareResponse,
    PatchRequest,
    PatchResponse,
    AnalysisCommand,
)
from app.services.analysis import run_analysis

router = APIRouter(prefix="/agent", tags=["agent"])


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


@router.post("/patch", response_model=PatchResponse, response_model_by_alias=True)
async def generate_patch(request: PatchRequest) -> PatchResponse:
    # TODO[Agent Core - 由你实现]: generate_patch / validate_patch / Human-in-the-loop。
    raise HTTPException(
        status_code=status.HTTP_501_NOT_IMPLEMENTED,
        detail=f"TODO[Agent Core]: 实现 analysisId={request.analysis_id} 的 Patch 生成流程",
    )


@router.post("/compare", response_model=CompareResponse, response_model_by_alias=True)
async def compare(request: CompareRequest) -> CompareResponse:
    # TODO[Agent Core - 由你实现]: 加载 before/after metrics 并生成 verdict。
    raise HTTPException(
        status_code=status.HTTP_501_NOT_IMPLEMENTED,
        detail=(
            "TODO[Agent Core]: 实现实验结果对比 "
            f"beforeTaskId={request.before_task_id}, afterTaskId={request.after_task_id}"
        ),
    )
