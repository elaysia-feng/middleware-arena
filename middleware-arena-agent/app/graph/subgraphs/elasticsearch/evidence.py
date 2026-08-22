"""Elasticsearch 专项证据处理。"""

import json
from typing import Any

from app.graph.state import AnalysisState

ES_CATEGORIES = {"ELASTICSEARCH_REJECTED", "ELASTICSEARCH_DEEP_PAGING"}
ES_KEYWORDS = (
    "elasticsearch", "search_after", "max_result_window", "all shards failed",
    "circuit_breaking_exception", "refresh_interval", "bulk", "mapping", "分片", "深分页",
)


def collect_all_evidence(state: AnalysisState) -> list[dict[str, Any]]:
    """合并主图与 reducer 证据，并按 evidenceId 去重。"""
    merged: dict[str, dict[str, Any]] = {}
    combined = [*(state.get("merged_evidence") or []), *(state.get("evidence") or [])]
    for index, item in enumerate(combined):
        if isinstance(item, dict):
            merged[str(item.get("id") or f"anonymous:{index}")] = item
    return list(merged.values())


def is_elasticsearch_signal(item: dict[str, Any]) -> bool:
    """根据结构化类别和关键字判断证据是否属于 Elasticsearch。"""
    data = item.get("data") if isinstance(item.get("data"), dict) else {}
    if str(data.get("category") or "").upper() in ES_CATEGORIES:
        return True
    text = json.dumps(item, ensure_ascii=False, default=str).lower()
    return any(keyword in text for keyword in ES_KEYWORDS)
