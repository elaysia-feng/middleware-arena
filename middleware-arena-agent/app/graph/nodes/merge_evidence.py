"""并行分析证据合并节点。

指标、日志、代码和相似实验节点会并行写入 evidence；本节点负责最终去重、排序和数据覆盖统计，
但不在这里生成瓶颈结论。
"""

from typing import Any

from app.graph.state import AnalysisState


LEVEL_PRIORITY = {"critical": 0, "warning": 1, "info": 2}
EXPECTED_SOURCES = {"metrics", "logs", "code_diff", "similar_experiments"}


def merge_evidence(state: AnalysisState) -> dict[str, Any]:
    """合并证据并生成后续专家节点可直接使用的摘要。"""
    raw_evidence = state.get("evidence") or []
    evidence_by_id: dict[str, dict[str, Any]] = {}
    for index, item in enumerate(raw_evidence[:500]):
        if not isinstance(item, dict):
            continue
        evidence_id = str(item.get("id") or f"anonymous:{index}")
        normalized = dict(item)
        normalized["id"] = evidence_id
        normalized["source"] = str(item.get("source") or "unknown")
        level = str(item.get("level") or "info").lower()
        normalized["level"] = level if level in LEVEL_PRIORITY else "info"
        normalized["message"] = str(item.get("message") or "")[:1000]
        normalized["data"] = item.get("data") if isinstance(item.get("data"), dict) else {}
        evidence_by_id[evidence_id] = normalized

    merged = sorted(
        evidence_by_id.values(),
        key=lambda item: LEVEL_PRIORITY[item["level"]],
    )[:200]

    level_counts = {"critical": 0, "warning": 0, "info": 0}
    source_counts: dict[str, int] = {}
    for item in merged:
        level_counts[item["level"]] += 1
        source = item["source"]
        source_counts[source] = source_counts.get(source, 0) + 1

    limitations: list[str] = []
    evidence_ids = {item["id"] for item in merged}
    present_sources = set(source_counts)
    missing_sources = sorted(EXPECTED_SOURCES - present_sources)
    for source in missing_sources:
        limitations.append(f"缺少 {source} 节点证据")
    if "log:unavailable" in evidence_ids:
        limitations.append("实验日志不可用")
    if "code:no-diff" in evidence_ids:
        limitations.append("没有代码 Diff")
    if "similar:unavailable" in evidence_ids:
        limitations.append("相似实验检索不可用")
    elif "similar:not-found" in evidence_ids:
        limitations.append("没有找到历史相似实验")

    missing_metric_count = sum(
        1
        for item in merged
        if item["source"] == "metrics" and item["data"].get("current") is None
    )
    if missing_metric_count:
        limitations.append(f"缺少 {missing_metric_count} 项当前指标")

    coverage_score = 1.0
    coverage_score -= len(missing_sources) * 0.20
    if "log:unavailable" in evidence_ids:
        coverage_score -= 0.20
    if "code:no-diff" in evidence_ids:
        coverage_score -= 0.15
    if "similar:unavailable" in evidence_ids:
        coverage_score -= 0.15
    elif "similar:not-found" in evidence_ids:
        coverage_score -= 0.05
    coverage_score -= min(missing_metric_count * 0.05, 0.20)
    coverage_score = round(max(0.0, min(coverage_score, 1.0)), 2)

    dominant_signals = [
        {
            "id": item["id"],
            "source": item["source"],
            "level": item["level"],
            "message": item["message"],
        }
        for item in merged
        if item["level"] in {"critical", "warning"}
    ][:10]

    return {
        "merged_evidence": merged,
        "evidence_summary": {
            "total": len(merged),
            "critical": level_counts["critical"],
            "warning": level_counts["warning"],
            "info": level_counts["info"],
            "sourceCounts": source_counts,
            "missingSources": missing_sources,
            "coverageScore": coverage_score,
            "limitations": limitations,
            "dominantSignals": dominant_signals,
        },
    }
