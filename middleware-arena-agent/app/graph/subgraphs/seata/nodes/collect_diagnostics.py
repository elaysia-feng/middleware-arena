"""Seata 关联数据库诊断节点。"""

from typing import Any

from app.graph.state import AnalysisState
from app.tools.providers import AnalysisToolProvider
from app.tools.tool_schemas import MySqlDiagnosticsInput, ToolResult


async def collect_seata_diagnostics(
    state: AnalysisState,
    *,
    provider: AnalysisToolProvider | None = None,
) -> dict[str, Any]:
    """查询锁等待、慢 SQL 和 undo_log；不可用时保留缺失证据说明。"""
    if provider is None:
        return {"seata_diagnostics": _unavailable("Seata 关联数据库诊断 Provider 尚未接入")}
    task_id = state.get("task_id")
    if not isinstance(task_id, int) or task_id <= 0:
        return {"seata_diagnostics": _unavailable("缺少有效 task_id")}
    try:
        result = await provider.get_mysql_diagnostics(MySqlDiagnosticsInput(task_id=task_id))
        if not isinstance(result, ToolResult):
            result = ToolResult.model_validate(result)
        diagnostics = result.model_dump(mode="json")
        diagnostics["toolType"] = "mysql_transaction_diagnostics"
        return {"seata_diagnostics": diagnostics}
    except Exception as error:
        return {"seata_diagnostics": _unavailable(f"Seata 关联数据库诊断失败：{type(error).__name__}")}


def _unavailable(summary: str) -> dict[str, Any]:
    return {
        "status": "unavailable",
        "toolRequired": True,
        "summary": summary,
        "data": {},
        "evidence": [],
        "warnings": ["当前缺少锁等待、慢 SQL 和 undo_log 实例数据"],
    }
