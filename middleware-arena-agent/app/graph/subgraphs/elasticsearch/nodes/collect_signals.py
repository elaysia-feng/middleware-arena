"""Elasticsearch 专项信号收集节点。"""

from typing import Any

from app.graph.state import AnalysisState
from app.graph.subgraphs.elasticsearch.evidence import collect_all_evidence, is_elasticsearch_signal


def collect_elasticsearch_signals(state: AnalysisState) -> dict[str, Any]:
    """筛选 ES 专项证据，并显式标记当前缺少实例级诊断数据。"""
    signals = [item for item in collect_all_evidence(state) if is_elasticsearch_signal(item)]
    return {
        "elasticsearch_signals": signals,
        "elasticsearch_diagnostics": {
            "status": "context_only",
            "summary": f"发现 {len(signals)} 条 Elasticsearch 相关证据" if signals else "没有 Elasticsearch 专项异常信号",
            "data": {}, "evidence": [],
            "warnings": ["当前未接入 Elasticsearch Profile、节点和分片诊断接口"],
        },
    }
