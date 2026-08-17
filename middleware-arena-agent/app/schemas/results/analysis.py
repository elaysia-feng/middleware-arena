"""Analysis Service Result 模型。

1. `AnalysisResult` 是 service 层内部返回 DTO / Result，不直接绑定某个传输协议。
2. HTTP 入口将它转换为 AnalyzeResponse。
3. MQ 入口将它转换为 AgentAnalysisStatusMessage。
"""

from typing import Any

from pydantic import BaseModel, Field


class AnalysisResult(BaseModel):
    analysis_id: int | None = None
    task_id: int
    status: str = "SUCCESS"
    trace_id: str | None = None
    data: dict[str, Any] = Field(default_factory=dict)
