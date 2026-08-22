"""RabbitMQ 专项信号收集节点。"""

from typing import Any

from app.graph.state import AnalysisState
from app.graph.subgraphs.rabbitmq.evidence import collect_all_evidence, is_rabbitmq_signal


def collect_rabbitmq_signals(state: AnalysisState) -> dict[str, Any]:
    """筛选 RabbitMQ 证据，并决定是否需要调用实例诊断工具。"""
    signals = [item for item in collect_all_evidence(state) if is_rabbitmq_signal(item)]
    return {
        "rabbitmq_signals": signals,
        "rabbitmq_diagnostics": {
            "status": "pending" if signals else "skipped",
            "toolRequired": bool(signals),
            "summary": f"发现 {len(signals)} 条 RabbitMQ 相关证据" if signals else "没有 RabbitMQ 专项异常信号",
            "data": {}, "evidence": [], "warnings": [],
        },
    }


def route_rabbitmq_diagnostics(state: AnalysisState) -> str:
    """有专项信号时先查 Broker，否则直接进入专家诊断。"""
    diagnostics = state.get("rabbitmq_diagnostics") or {}
    return "collect_rabbitmq_diagnostics" if diagnostics.get("toolRequired") else "diagnose_rabbitmq"
