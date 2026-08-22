"""Seata 专项证据处理。"""

import json
from typing import Any

from app.graph.state import AnalysisState

SEATA_CATEGORIES = {"SEATA_TRANSACTION", "DATABASE_LOCK", "TRANSACTION_REMOTE_CALL"}
SEATA_KEYWORDS = (
    "seata", "globaltransactional", "global transaction", "branch rollback",
    "undo_log", "lockkey", "xid", "全局事务", "分支事务",
)


def collect_all_evidence(state: AnalysisState) -> list[dict[str, Any]]:
    """汇总主图证据，并按 evidenceId 消除并行 reducer 的重复项。"""
    merged: dict[str, dict[str, Any]] = {}
    combined = [*(state.get("merged_evidence") or []), *(state.get("evidence") or [])]
    for index, item in enumerate(combined):
        if isinstance(item, dict):
            merged[str(item.get("id") or f"anonymous:{index}")] = item
    return list(merged.values())


def is_seata_signal(item: dict[str, Any]) -> bool:
    """识别全局事务、分支回滚、锁等待和 undo_log 相关证据。"""
    data = item.get("data") if isinstance(item.get("data"), dict) else {}
    if str(data.get("category") or "").upper() in SEATA_CATEGORIES:
        return True
    text = json.dumps(item, ensure_ascii=False, default=str).lower()
    return any(keyword in text for keyword in SEATA_KEYWORDS)


def build_tool_evidence(diagnostics: dict[str, Any]) -> list[dict[str, Any]]:
    """把数据库事务诊断转换为 Judge 可引用的标准证据。"""
    if diagnostics.get("status") not in {"ok", "partial"}:
        return []
    return [
        {
            "id": f"seata:tool:{index}",
            "source": "seata_diagnostics",
            "level": "warning",
            "message": str(message),
            "data": diagnostics.get("data", {}),
        }
        for index, message in enumerate(diagnostics.get("evidence", [])[:20], 1)
    ]
