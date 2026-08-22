"""指标对比节点。

本节点只做确定性计算，不调用 LLM：
1. 对比当前实验与基线的 QPS、P95、错误率、CPU、内存。
2. 输出结构化 metric_findings，供前端表格和后续专家节点使用。
3. 输出可引用的 evidence，但不在这里直接判断最终瓶颈。
"""

from typing import Any

from app.graph.state import AnalysisState


METRIC_RULES = (
    {"key": "qps", "label": "QPS", "unit": "req/s", "higher_is_better": True},
    {"key": "p95Ms", "label": "P95 延迟", "unit": "ms", "higher_is_better": False},
    {"key": "errorRate", "label": "错误率", "unit": "ratio", "higher_is_better": False},
    {"key": "avgCpu", "label": "平均 CPU", "unit": "ratio", "higher_is_better": False},
    {"key": "peakMemoryMb", "label": "峰值内存", "unit": "MB", "higher_is_better": False},
)


def analyze_metrics(state: AnalysisState) -> dict[str, list[dict[str, Any]]]:
    """计算五项核心指标变化，并返回本节点负责更新的 State 字段。"""
    current_metrics = state.get("metrics") or {}
    baseline_metrics = state.get("baseline_metrics") or {}
    findings: list[dict[str, Any]] = []
    evidence: list[dict[str, Any]] = []

    for rule in METRIC_RULES:
        key = str(rule["key"])
        current = _to_number(current_metrics.get(key))
        baseline = _to_number(baseline_metrics.get(key))
        finding = _compare_metric(
            key=key,
            label=str(rule["label"]),
            unit=str(rule["unit"]),
            higher_is_better=bool(rule["higher_is_better"]),
            current=current,
            baseline=baseline,
        )
        findings.append(finding)
        evidence.append({
            "id": f"metric:{key}",
            "source": "metrics",
            "level": finding["level"],
            "message": finding["summary"],
            "data": {
                "metric": key,
                "current": current,
                "baseline": baseline,
                "delta": finding["delta"],
                "changePercent": finding["changePercent"],
            },
        })

    return {
        "metric_findings": findings,
        "evidence": evidence,
    }


def _compare_metric(
    *,
    key: str,
    label: str,
    unit: str,
    higher_is_better: bool,
    current: float | None,
    baseline: float | None,
) -> dict[str, Any]:
    if current is None:
        return {
            "metric": key,
            "label": label,
            "unit": unit,
            "current": None,
            "baseline": baseline,
            "delta": None,
            "changePercent": None,
            "direction": "MISSING",
            "assessment": "UNKNOWN",
            "level": "warning",
            "summary": f"{label} 缺少当前实验数据",
        }

    if baseline is None:
        return {
            "metric": key,
            "label": label,
            "unit": unit,
            "current": current,
            "baseline": None,
            "delta": None,
            "changePercent": None,
            "direction": "NO_BASELINE",
            "assessment": "UNKNOWN",
            "level": _current_value_level(key, current),
            "summary": f"{label}当前值为 {_format_value(current, unit)}，没有可用基线",
        }

    delta = round(current - baseline, 4)
    change_percent = None
    if baseline != 0:
        change_percent = round(delta / abs(baseline) * 100, 2)

    if abs(delta) < 0.0001:
        direction = "UNCHANGED"
        assessment = "STABLE"
    elif delta > 0:
        direction = "INCREASED"
        assessment = "IMPROVED" if higher_is_better else "REGRESSED"
    else:
        direction = "DECREASED"
        assessment = "REGRESSED" if higher_is_better else "IMPROVED"

    level = _comparison_level(key, current, change_percent, assessment)
    change_text = f"{change_percent:+.2f}%" if change_percent is not None else "无法计算百分比"
    summary = (
        f"{label}从 {_format_value(baseline, unit)} 变为 {_format_value(current, unit)}，"
        f"变化 {change_text}"
    )
    return {
        "metric": key,
        "label": label,
        "unit": unit,
        "current": current,
        "baseline": baseline,
        "delta": delta,
        "changePercent": change_percent,
        "direction": direction,
        "assessment": assessment,
        "level": level,
        "summary": summary,
    }


def _comparison_level(
    key: str,
    current: float,
    change_percent: float | None,
    assessment: str,
) -> str:
    if key == "errorRate":
        if current >= 0.05:
            return "critical"
        if current >= 0.01:
            return "warning"
    if key == "avgCpu":
        if current >= 0.90:
            return "critical"
        if current >= 0.75:
            return "warning"
    if assessment != "REGRESSED" or change_percent is None:
        return "info"
    regression_percent = abs(change_percent)
    if regression_percent >= 30:
        return "critical"
    if regression_percent >= 10:
        return "warning"
    return "info"


def _current_value_level(key: str, current: float) -> str:
    if key == "errorRate" and current >= 0.05:
        return "critical"
    if key == "errorRate" and current >= 0.01:
        return "warning"
    if key == "avgCpu" and current >= 0.90:
        return "critical"
    if key == "avgCpu" and current >= 0.75:
        return "warning"
    return "info"


def _format_value(value: float, unit: str) -> str:
    if unit == "ratio":
        return f"{value:.2%}"
    if unit == "ms":
        return f"{value:.2f} ms"
    if unit == "MB":
        return f"{value:.2f} MB"
    return f"{value:.2f} req/s"


def _to_number(value: Any) -> float | None:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        return None
    return float(value)
