"""加载实验上下文节点。

1. 根据 task_id/version_id 拉取实验、版本、代码快照与运行参数。
2. 拉取当前指标、基线指标和必要日志。
3. 将外部数据标准化后写入 AnalysisState。

"""

from typing import Any

from app.clients.experiment import experiment_client
from app.graph.state import AnalysisState
from app.schemas.analysis import AnalysisContext


async def load_context(state: AnalysisState) -> dict[str, Any]:
    """加载并校验固定实验事实，返回本节点负责写入的 State 字段。"""
    raw_context = await experiment_client.get_analysis_context(
        task_id=state["task_id"],
        baseline_task_id=state.get("baseline_task_id"),
    )
    context = AnalysisContext.model_validate(raw_context)
    if context.task_id != state["task_id"]:
        raise ValueError("experiment-service 返回的 taskId 与请求不一致")

    result: dict[str, Any] = {
        "user_id": context.user_id,
        "version_id": context.version_id,
        "middleware_type": context.middleware_type.upper(),
        "config": context.config,
        "files": context.files,
        "code_diff": context.code_diff,
        "metrics": context.metrics,
        "baseline_metrics": context.baseline_metrics,
        # 双重限制日志数量，防止未来 Java 接口调整后把无限日志送入后续 LLM 节点。
        "logs": context.logs[:200],
    }
    if context.baseline_task_id is not None:
        result["baseline_task_id"] = context.baseline_task_id
    return result
