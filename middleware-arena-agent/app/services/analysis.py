"""HTTP 与 RabbitMQ 共用的 Agent 分析业务编排入口。"""

from typing import Any

from app.clients.langfuse import build_langfuse_run_config
from app.core.config import get_settings
from app.graph.builder import analysis_graph
from app.schemas.analysis import AnalysisCommand, AnalysisResult


async def run_analysis(command: AnalysisCommand) -> AnalysisResult:
    """执行完整诊断图，并转换为与传输协议无关的结果。"""
    settings = get_settings()
    initial_state = _build_initial_state(command)
    final_state = await analysis_graph.ainvoke(
        initial_state,
        config={
            **build_langfuse_run_config(
                run_name="middleware-analysis-workflow",
                metadata={
                    "analysisId": command.analysis_id,
                    "taskId": command.task_id,
                    "versionId": command.version_id,
                    "middlewareType": command.middleware_type,
                    "analysisType": command.analysis_type,
                    "triggerType": command.trigger_type,
                    "dispatchId": command.dispatch_id,
                },
            ),
            # 当前主图没有自动重跑，但仍限制最大递归步数，防止未来错误接边无限循环。
            "recursion_limit": max(
                30,
                settings.agent_max_analysis_iterations * 20,
            ),
        },
    )
    return AnalysisResult(
        analysis_id=command.analysis_id,
        task_id=command.task_id,
        status="SUCCESS",
        trace_id=final_state.get("trace_id"),
        data=_build_result_data(final_state),
    )


def _build_initial_state(command: AnalysisCommand) -> dict[str, Any]:
    """只把已知命令字段写入 State，实验事实统一由 load_context 查询。"""
    settings = get_settings()
    state: dict[str, Any] = {
        "task_id": command.task_id,
        "analysis_type": command.analysis_type,
        "trigger_type": command.trigger_type,
        "iteration": 0,
        "max_iterations": settings.agent_max_analysis_iterations,
        "patch_confidence_threshold": (
            settings.agent_patch_confidence_threshold
        ),
    }
    optional_fields = {
        "analysis_id": command.analysis_id,
        "user_id": command.user_id,
        "version_id": command.version_id,
        "baseline_task_id": command.baseline_task_id,
        "middleware_type": command.middleware_type,
        "dispatch_id": command.dispatch_id,
    }
    state.update(
        {
            name: value
            for name, value in optional_fields.items()
            if value is not None
        }
    )
    return state


def _build_result_data(state: dict[str, Any]) -> dict[str, Any]:
    """过滤原始代码和日志，只返回前端真正需要的诊断结果。"""
    return {
        "middlewareType": state.get("middleware_type"),
        "metricFindings": state.get("metric_findings", []),
        "logFindings": state.get("log_findings", []),
        "codeFindings": state.get("code_findings", []),
        "similarExperiments": state.get("similar_experiments", []),
        "evidenceSummary": state.get("evidence_summary", {}),
        "hypotheses": state.get("ranked_hypotheses", []),
        "bottleneck": state.get("bottleneck", {}),
        "confidence": state.get("confidence", 0),
        "suggestions": state.get("suggestions", []),
        "confidenceRoute": state.get("confidence_route"),
        "confidenceRouteReason": state.get("confidence_route_reason"),
        "patches": state.get("patches", []),
        "patchSummary": state.get("patch_summary", {}),
        "nextAction": state.get("next_action"),
        "report": state.get("report", ""),
        "reportSummary": state.get("report_summary", {}),
    }
