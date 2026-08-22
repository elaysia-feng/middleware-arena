"""RabbitMQ Broker 深度诊断节点。"""

from typing import Any

from app.graph.state import AnalysisState
from app.tools.providers import AnalysisToolProvider
from app.tools.tool_schemas import RabbitMqDiagnosticsInput, ToolResult


async def collect_rabbitmq_diagnostics(
    state: AnalysisState,
    *,
    provider: AnalysisToolProvider | None = None,
) -> dict[str, Any]:
    """按 taskId 获取 Broker 实例诊断；Provider 缺失或异常时返回可降级状态。"""
    if provider is None:
        return {"rabbitmq_diagnostics": _unavailable("RabbitMQ 诊断 Provider 尚未接入")}
    task_id = state.get("task_id")
    if not isinstance(task_id, int) or task_id <= 0:
        return {"rabbitmq_diagnostics": _unavailable("缺少有效 task_id")}
    try:
        result = await provider.get_rabbitmq_diagnostics(RabbitMqDiagnosticsInput(task_id=task_id))
        if not isinstance(result, ToolResult):
            result = ToolResult.model_validate(result)
        return {"rabbitmq_diagnostics": result.model_dump(mode="json")}
    except Exception as error:
        return {"rabbitmq_diagnostics": _unavailable(f"RabbitMQ 诊断失败：{type(error).__name__}")}


def _unavailable(summary: str) -> dict[str, Any]:
    return {
        "status": "unavailable",
        "toolRequired": True,
        "summary": summary,
        "data": {},
        "evidence": [],
        "warnings": ["当前缺少 RabbitMQ Broker 实例侧数据"],
    }
