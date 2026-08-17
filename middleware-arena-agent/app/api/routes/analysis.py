"""Agent 分析相关 HTTP Router。

这一层只负责 HTTP 协议：
1. FastAPI 接收/校验 Request。
2. 把 Request 转成业务层统一的 AnalysisCommand。
3. 调 Service。
4. 把 AnalysisResult 转成 HTTP Response。

不要在 Router 里直接写 LangGraph Node、RabbitMQ、数据库访问，否则 Controller 层会越来越重。
"""

from fastapi import APIRouter, HTTPException, status

from app.core.config import get_settings
from app.schemas.analysis import (
    AnalysisCommand,
    AnalyzeRequest,
    AnalyzeResponse,
    CompareRequest,
    CompareResponse,
    PatchRequest,
    PatchResponse,
)
from app.services.analysis import run_analysis

# prefix 来自 .env 中的 AGENT_HTTP_PREFIX，默认 /agent。
# 因此前端/Gateway 最终访问的是 /agent/analyze、/agent/patch、/agent/compare。
router = APIRouter(
    prefix=get_settings().agent_http_prefix,
    tags=["agent"],
)


@router.post(
    "/analyze",
    response_model=AnalyzeResponse,
    # Response 内 Python 使用 snake_case，但对外 JSON 使用 Field(alias=...) 定义的 camelCase。
    response_model_by_alias=True,
)
async def analyze(request: AnalyzeRequest) -> AnalyzeResponse:
    """用户主动触发一次实验分析。

    前端只传 taskId/baselineTaskId；userId/versionId/middlewareType 等可信数据
    后续由 Agent 通过 experiment-service 查询，不直接信任客户端提交。
    """

    # HTTP Request -> Service Command。
    # trigger_type 明确写 MANUAL，用于和 Runner 完成后的 AUTO MQ 分析区分。
    command = AnalysisCommand(
        task_id=request.task_id,
        baseline_task_id=request.baseline_task_id,
        trigger_type="MANUAL",
    )

    try:
        result = await run_analysis(command)
    except NotImplementedError as exc:
        # 核心 Graph 尚未实现时明确返回 501，而不是返回一个伪 SUCCESS。
        raise HTTPException(
            status_code=status.HTTP_501_NOT_IMPLEMENTED,
            detail=str(exc),
        ) from exc

    # Service Result -> HTTP Response。
    return AnalyzeResponse(
        task_id=result.task_id,
        analysis_id=result.analysis_id,
        status=result.status,
        trace_id=result.trace_id,
        result=result.data,
    )


@router.post(
    "/patch",
    response_model=PatchResponse,
    response_model_by_alias=True,
)
async def generate_patch(request: PatchRequest) -> PatchResponse:
    """根据已有分析结果生成候选 Patch。

    注意：这里的目标是“生成候选修改”，不是直接覆盖 experiment_version。
    用户确认后才允许 Java experiment-service 创建新版本。
    """

    # TODO[Agent Core - 由你实现]:
    # 1. 根据 analysisId 加载 bottleneck / evidence / hypotheses。
    # 2. 调 graph/nodes/generate_patch.py。
    # 3. validate_patch：做格式/语法/构建层面的基础校验。
    # 4. 持久化 experiment_patch(CREATED)。
    # 5. Human-in-the-loop 接受/拒绝后再创建 experiment_version。
    raise HTTPException(
        status_code=status.HTTP_501_NOT_IMPLEMENTED,
        detail=f"TODO[Agent Core]: 实现 analysisId={request.analysis_id} 的 Patch 生成流程",
    )


@router.post(
    "/compare",
    response_model=CompareResponse,
    response_model_by_alias=True,
)
async def compare(request: CompareRequest) -> CompareResponse:
    """比较优化前后的两次实验，判断 Patch 是否真的有效。"""

    # TODO[Agent Core - 由你实现]:
    # 1. 加载 beforeTaskId / afterTaskId 两份 experiment_result。
    # 2. 计算 QPS / P95 / Error / CPU / Memory 的 delta 和 ratio。
    # 3. 结合指标判断优化是否有效，以及是否出现副作用。
    # 4. 返回结构化 verdict，而不是只输出一段 LLM 自然语言。
    raise HTTPException(
        status_code=status.HTTP_501_NOT_IMPLEMENTED,
        detail=(
            "TODO[Agent Core]: 实现实验结果对比 "
            f"beforeTaskId={request.before_task_id}, afterTaskId={request.after_task_id}"
        ),
    )
