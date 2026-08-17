"""Agent HTTP Request / Response 模型。

命名约定：
1. `*Request` 只用于 FastAPI HTTP 入参。
2. `*Response` 只用于 FastAPI HTTP 出参，可理解为 Java 中的 Response DTO / VO。
3. HTTP 模型不要混入 MQ 字段、LangGraph State 或数据库字段。
"""

from typing import Any

from pydantic import BaseModel, ConfigDict, Field


class AnalyzeRequest(BaseModel):
    """POST /agent/analyze 的请求体。"""

    model_config = ConfigDict(populate_by_name=True, extra="forbid")

    task_id: int = Field(alias="taskId", gt=0)
    baseline_task_id: int | None = Field(default=None, alias="baselineTaskId", gt=0)


class AnalyzeResponse(BaseModel):
    """POST /agent/analyze 的响应体。"""

    model_config = ConfigDict(populate_by_name=True)

    task_id: int = Field(alias="taskId")
    analysis_id: int | None = Field(default=None, alias="analysisId")
    status: str
    trace_id: str | None = Field(default=None, alias="traceId")
    result: dict[str, Any] = Field(default_factory=dict)


class PatchRequest(BaseModel):
    """POST /agent/patch 的请求体。"""

    model_config = ConfigDict(populate_by_name=True, extra="forbid")

    analysis_id: int = Field(alias="analysisId", gt=0)


class PatchResponse(BaseModel):
    """Patch 成功后的 HTTP 响应骨架。"""

    model_config = ConfigDict(populate_by_name=True)

    analysis_id: int = Field(alias="analysisId")
    patch_id: int | None = Field(default=None, alias="patchId")
    status: str

    # TODO[Agent Core]: 后续补 files / summary / validation 等真正业务字段。


class CompareRequest(BaseModel):
    """POST /agent/compare 的请求体。"""

    model_config = ConfigDict(populate_by_name=True, extra="forbid")

    before_task_id: int = Field(alias="beforeTaskId", gt=0)
    after_task_id: int = Field(alias="afterTaskId", gt=0)


class CompareResponse(BaseModel):
    """优化前后对比的 HTTP 响应骨架。"""

    model_config = ConfigDict(populate_by_name=True)

    before_task_id: int = Field(alias="beforeTaskId")
    after_task_id: int = Field(alias="afterTaskId")
    status: str
    result: dict[str, Any] = Field(default_factory=dict)

    # TODO[Agent Core]: 后续补 qps/p95/errorRate delta 与 verdict。
