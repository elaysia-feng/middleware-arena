"""LangGraph 状态定义（骨架占位）。

TODO[AI Agent]：补全 AnalysisState 字段，供 LangGraph 节点链共享：
    experiment_id, code_diff, config, metrics, baseline_metrics,
    similar_experiments, bottleneck, confidence, evidence, suggestions, report
"""
from typing import Any, TypedDict


class AnalysisState(TypedDict, total=False):
    """一次实验分析的全量状态。"""

    experiment_id: int
    code_diff: str
    config: dict[str, Any]
    metrics: dict[str, Any]
    baseline_metrics: dict[str, Any]

    bottleneck: str
    confidence: float
    evidence: list[str]
    suggestions: list[str]
    report: str
