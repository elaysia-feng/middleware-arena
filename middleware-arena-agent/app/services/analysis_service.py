"""HTTP / MQ 共用的 Agent 分析业务入口。

这里刻意只把“入口契约”实现好，真正的 Agent 推理逻辑留给 graph/ 下完成。

1. HTTP / MQ 都转换为 AnalysisCommand。
2. 后续只允许在 run_analysis 中启动 LangGraph，避免两套入口逻辑漂移。
3. 返回统一 AnalysisResult，HTTP 可直接响应，MQ 可序列化后回传。

TODO[核心逻辑-由你实现]:
- [ ] load_context：通过 experiment internal API 拉 version / metrics / diff / logs。
- [ ] 编译并执行 LangGraph，接入 Redis/RabbitMQ/Seata/ES SubGraph。
- [ ] 注入 Langfuse callback / trace metadata。
- [ ] 生成 evidence / hypothesis / bottleneck / patch / report。
- [ ] 控制 max_iterations 与 Human-in-the-loop。
"""

from typing import Any, Literal

from pydantic import BaseModel, ConfigDict


class AnalysisCommand(BaseModel):
    model_config = ConfigDict(extra="forbid")

    task_id: int
    analysis_id: int | None = None
    user_id: int | None = None
    version_id: int | None = None
    baseline_task_id: int | None = None
    middleware_type: str | None = None
    analysis_type: str = "PERFORMANCE_DIAGNOSIS"
    trigger_type: Literal["AUTO", "MANUAL", "RETRY"] = "MANUAL"
    dispatch_id: str | None = None


class AnalysisResult(BaseModel):
    analysis_id: int | None = None
    task_id: int
    status: str = "SUCCESS"
    trace_id: str | None = None
    data: dict[str, Any] = {}


async def run_analysis(command: AnalysisCommand) -> AnalysisResult:
    """启动一次完整分析。

    基础设施已经把 HTTP/MQ 输入统一到这里；接下来由你从这里接 LangGraph。
    """
    raise NotImplementedError(
        "TODO[Agent Core]: 在 app/services/analysis_service.py::run_analysis 中接入 LangGraph"
    )
