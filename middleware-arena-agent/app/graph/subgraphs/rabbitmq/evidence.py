"""RabbitMQ 专项证据处理。"""

import json
from typing import Any

from app.graph.state import AnalysisState

RABBITMQ_CATEGORIES = {"RABBITMQ_DELIVERY", "RABBITMQ_DELIVERY_CONFIG"}
RABBITMQ_KEYWORDS = (
    "rabbitmq", "rabbittemplate", "basic.ack", "basic.nack", "prefetch",
    "unacked", "queue overflow", "consumer", "publisher confirm", "消息积压",
)


def collect_all_evidence(state: AnalysisState) -> list[dict[str, Any]]:
    """汇总主图证据，并保留同一 evidenceId 的最新内容。"""
    merged: dict[str, dict[str, Any]] = {}
    combined = [*(state.get("merged_evidence") or []), *(state.get("evidence") or [])]
    for index, item in enumerate(combined):
        if isinstance(item, dict):
            merged[str(item.get("id") or f"anonymous:{index}")] = item
    return list(merged.values())


def is_rabbitmq_signal(item: dict[str, Any]) -> bool:
    """识别投递、确认、积压和消费者相关的 RabbitMQ 证据。"""
    data = item.get("data") if isinstance(item.get("data"), dict) else {}
    if str(data.get("category") or "").upper() in RABBITMQ_CATEGORIES:
        return True
    text = json.dumps(item, ensure_ascii=False, default=str).lower()
    return any(keyword in text for keyword in RABBITMQ_KEYWORDS)


def build_tool_evidence(diagnostics: dict[str, Any]) -> list[dict[str, Any]]:
    """把 Broker 诊断结果转换为主图可引用的标准证据。"""
    if diagnostics.get("status") not in {"ok", "partial"}:
        return []
    return [
        {
            "id": f"rabbitmq:tool:{index}",
            "source": "rabbitmq_diagnostics",
            "level": "warning",
            "message": str(message),
            "data": diagnostics.get("data", {}),
        }
        for index, message in enumerate(diagnostics.get("evidence", [])[:20], 1)
    ]
