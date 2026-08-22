"""Redis 专家使用的证据筛选和 Tool 证据转换。"""

import json
from typing import Any

from app.graph.state import AnalysisState


REDIS_DIRECT_CATEGORIES = {
    "REDIS_ERROR",
    "REDIS_FULL_SCAN",
}

REDIS_KEYWORDS = (
    "redis",
    "jedis",
    "lettuce",
    "redisson",
    "cache",
    "缓存",
    "bigkey",
    "hotkey",
    "slowlog",
    "keys *",
)


def collect_all_evidence(state: AnalysisState) -> list[dict[str, Any]]:
    """合并主图原始证据和 Reducer 整理后的证据。"""
    combined = [*(state.get("merged_evidence") or []), *(state.get("evidence") or [])]
    deduplicated: dict[str, dict[str, Any]] = {}
    for index, item in enumerate(combined):
        if not isinstance(item, dict):
            continue
        evidence_id = str(item.get("id") or f"anonymous:{index}")
        deduplicated[evidence_id] = item
    return list(deduplicated.values())


def is_redis_signal(evidence: dict[str, Any]) -> bool:
    """判断一条证据是否明确带有 Redis 语义。"""
    data = evidence.get("data") if isinstance(evidence.get("data"), dict) else {}
    category = str(data.get("category") or "").upper()
    if category in REDIS_DIRECT_CATEGORIES:
        return True
    searchable = json.dumps(evidence, ensure_ascii=False, default=str).lower()
    return any(keyword in searchable for keyword in REDIS_KEYWORDS)


def build_tool_evidence(diagnostics: dict[str, Any]) -> list[dict[str, Any]]:
    """把 Redis Provider 结果转换成主图可以引用的证据。"""
    if diagnostics.get("status") not in {"ok", "partial"}:
        return []
    messages = [str(item) for item in diagnostics.get("evidence", [])[:20] if str(item).strip()]
    return [
        {
            "id": f"redis:tool:{index}",
            "source": "redis_diagnostics",
            "level": "warning",
            "message": message,
            "data": diagnostics.get("data", {}),
        }
        for index, message in enumerate(messages, start=1)
    ]
