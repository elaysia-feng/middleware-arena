"""Redis 专项信号收集节点。"""

from typing import Any

from app.graph.state import AnalysisState
from app.graph.subgraphs.redis.evidence import collect_all_evidence, is_redis_signal


def collect_redis_signals(state: AnalysisState) -> dict[str, Any]:
    """筛选 Redis 信号，并判断是否需要实例深度诊断。"""
    all_evidence = collect_all_evidence(state)
    redis_signals = [item for item in all_evidence if is_redis_signal(item)]
    metric_regressions = [
        item
        for item in state.get("metric_findings", [])
        if item.get("assessment") == "REGRESSED"
        and item.get("level") in {"warning", "critical"}
    ]
    tool_required = bool(redis_signals or metric_regressions)

    if redis_signals:
        reason = f"发现 {len(redis_signals)} 条 Redis 相关证据"
    elif metric_regressions:
        reason = "指标明显退化，需要检查 Redis 实例侧数据"
    else:
        reason = "没有 Redis 专项异常信号，跳过实例深度诊断"

    return {
        "redis_signals": redis_signals,
        "redis_diagnostics": {
            "status": "pending" if tool_required else "skipped",
            "summary": reason,
            "toolRequired": tool_required,
            "data": {},
            "evidence": [],
            "warnings": [],
        },
    }


def route_redis_diagnostics(state: AnalysisState) -> str:
    """已有异常信号时调用 Tool，否则直接进入专家分析。"""
    diagnostics = state.get("redis_diagnostics") or {}
    return "collect_redis_diagnostics" if diagnostics.get("toolRequired") else "diagnose_redis"
