"""Analyze HTTP 请求/响应模型。"""

from typing import Any

from pydantic import BaseModel, ConfigDict, Field


class AnalyzeRequest(BaseModel):
    """POST /agent/analyze 请求体。"""

    model_config = ConfigDict(populate_by_name=True, extra="forbid")

    task_id: int = Field(alias="taskId", gt=0)
    baseline_task_id: int | None = Field(default=None, alias="baselineTaskId", gt=0)


class AnalyzeResponse(BaseModel):
    """POST /agent/analyze 响应体。"""

    model_config = ConfigDict(populate_by_name=True)

    task_id: int = Field(alias="taskId")
    analysis_id: int | None = Field(default=None, alias="analysisId")
    status: str
    trace_id: str | None = Field(default=None, alias="traceId")
    result: dict[str, Any] = Field(default_factory=dict)
