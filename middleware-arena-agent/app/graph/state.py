"""LangGraph AnalysisState。

命名约定：
1. State 只属于 LangGraph 运行时，不是 Request/Response/DTO/Entity。
2. AnalysisCommand 负责“启动参数”，AnalysisState 负责“图执行过程中的共享上下文”。
3. 节点间共享字段统一在这里声明，避免每个 Node 自己发明 dict key。

TODO[核心逻辑-由你实现]:
- [ ] 根据实际 SubGraph 确定 reducer，尤其 hypotheses/evidence/patches 等累加字段。
- [ ] 明确哪些字段进入 Checkpoint，哪些大对象只保存引用。
- [ ] 为 Evidence/Hypothesis/Patch 建结构化 Pydantic 模型，替代长期使用 dict。
"""

from typing import Any, TypedDict


class AnalysisState(TypedDict, total=False):
    # ---------- identity / command ----------
    analysis_id: int
    task_id: int
    user_id: int
    version_id: int
    baseline_task_id: int
    middleware_type: str
    analysis_type: str
    trigger_type: str
    dispatch_id: str

    # ---------- source context ----------
    config: dict[str, Any]
    files: list[dict[str, Any]]
    code_diff: list[dict[str, Any]]
    metrics: dict[str, Any]
    baseline_metrics: dict[str, Any]
    logs: list[str]

    # ---------- analysis intermediate ----------
    metric_findings: list[dict[str, Any]]
    code_findings: list[dict[str, Any]]
    hypotheses: list[dict[str, Any]]
    evidence: list[dict[str, Any]]

    # ---------- decision / optimization ----------
    bottleneck: dict[str, Any]
    confidence: float
    suggestions: list[dict[str, Any]]
    patches: list[dict[str, Any]]

    # ---------- loop / result ----------
    iteration: int
    max_iterations: int
    comparison: dict[str, Any]
    report: str
    trace_id: str
