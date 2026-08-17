"""Analysis Service Result 模型。"""

from typing import Any

from pydantic import BaseModel, Field


class AnalysisResult(BaseModel):
    """Service 层统一返回结果，可转换成 HTTP Response 或 MQ StatusMessage。"""

    analysis_id: int | None = None
    task_id: int
    status: str = "SUCCESS"
    trace_id: str | None = None
    data: dict[str, Any] = Field(default_factory=dict)
