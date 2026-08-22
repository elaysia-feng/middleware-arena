"""Redis 实例深度诊断 Tool 节点。"""

from typing import Any

from app.graph.state import AnalysisState
from app.tools.providers import AnalysisToolProvider
from app.tools.tool_schemas import RedisDiagnosticsInput, ToolResult


async def collect_redis_diagnostics(
    state: AnalysisState,
    *,
    provider: AnalysisToolProvider | None = None,
) -> dict[str, Any]:
    """通过受控 Provider 获取 BigKey、SlowLog 等实例侧证据。"""
    if provider is None:
        return {
            "redis_diagnostics": {
                "status": "unavailable",
                "summary": "Redis 诊断 Provider 尚未接入",
                "toolRequired": True,
                "data": {},
                "evidence": [],
                "warnings": ["当前仅能依据实验指标、日志和代码 Diff 分析"],
            }
        }

    task_id = state.get("task_id")
    if not isinstance(task_id, int) or task_id <= 0:
        return {
            "redis_diagnostics": {
                "status": "unavailable",
                "summary": "缺少有效 task_id，无法请求 Redis 深度诊断",
                "toolRequired": True,
                "data": {},
                "evidence": [],
                "warnings": ["Redis 实例侧证据缺失"],
            }
        }

    try:
        result = await provider.get_redis_diagnostics(RedisDiagnosticsInput(task_id=task_id))
        if not isinstance(result, ToolResult):
            result = ToolResult.model_validate(result)
        return {"redis_diagnostics": result.model_dump(mode="json")}
    except Exception as error:
        return {
            "redis_diagnostics": {
                "status": "unavailable",
                "summary": f"Redis 深度诊断调用失败：{type(error).__name__}",
                "toolRequired": True,
                "data": {},
                "evidence": [],
                "warnings": ["Redis Tool 不可用，专家已降级为已有证据分析"],
            }
        }
