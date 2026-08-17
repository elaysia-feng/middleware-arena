"""Analysis Service Command 模型。

1. `AnalysisCommand` 是 service 层内部 DTO / Command，不是 HTTP Request。
2. HTTP Request 与 MQ Message 都要先转换成 Command，再进入 analysis_service。
3. Command 只描述“要执行什么分析”，不保存 LangGraph 中间状态。
"""

from typing import Literal

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
