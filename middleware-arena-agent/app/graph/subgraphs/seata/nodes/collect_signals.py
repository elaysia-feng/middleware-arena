"""Seata 专项信号收集节点。"""

from typing import Any

from app.graph.state import AnalysisState
from app.graph.subgraphs.seata.evidence import collect_all_evidence, is_seata_signal


def collect_seata_signals(state: AnalysisState) -> dict[str, Any]:
    """筛选 Seata 专项证据，并决定是否需要数据库实例诊断。"""
    signals = [item for item in collect_all_evidence(state) if is_seata_signal(item)]
    return {
        "seata_signals": signals,
        "seata_diagnostics": {
            "status": "pending" if signals else "skipped",
            "toolRequired": bool(signals),
            "summary": f"发现 {len(signals)} 条 Seata 相关证据" if signals else "没有 Seata 专项异常信号",
            "data": {}, "evidence": [], "warnings": [],
        },
    }


def route_seata_diagnostics(state: AnalysisState) -> str:
    """存在事务信号时先补实例证据，否则直接进入专家诊断。"""
    diagnostics = state.get("seata_diagnostics") or {}
    return "collect_seata_diagnostics" if diagnostics.get("toolRequired") else "diagnose_seata"
