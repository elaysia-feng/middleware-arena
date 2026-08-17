"""Pydantic models for the analysis domain.

FastAPI projects usually group request/response models by business domain instead of
creating Java-style DTO/VO directories. Internal command/result models stay here too
because they are lightweight Pydantic boundary models for the same analysis domain.
"""

from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field


class AnalyzeRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="forbid")

    task_id: int = Field(alias="taskId", gt=0)
    baseline_task_id: int | None = Field(default=None, alias="baselineTaskId", gt=0)


class AnalyzeResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    task_id: int = Field(alias="taskId")
    analysis_id: int | None = Field(default=None, alias="analysisId")
    status: str
    trace_id: str | None = Field(default=None, alias="traceId")
    result: dict[str, Any] = Field(default_factory=dict)


class PatchRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="forbid")

    analysis_id: int = Field(alias="analysisId", gt=0)


class PatchResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    analysis_id: int = Field(alias="analysisId")
    patch_id: int | None = Field(default=None, alias="patchId")
    status: str

    # TODO[Agent Core]: 补 files / summary / validation 等业务字段。


class CompareRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="forbid")

    before_task_id: int = Field(alias="beforeTaskId", gt=0)
    after_task_id: int = Field(alias="afterTaskId", gt=0)


class CompareResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    before_task_id: int = Field(alias="beforeTaskId")
    after_task_id: int = Field(alias="afterTaskId")
    status: str
    result: dict[str, Any] = Field(default_factory=dict)

    # TODO[Agent Core]: 补 QPS/P95/Error/CPU/Memory delta 与 verdict。


class AnalysisCommand(BaseModel):
    """HTTP/MQ 入口统一转换后的 service 输入。"""

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
    """analysis service 的协议无关返回值。"""

    analysis_id: int | None = None
    task_id: int
    status: str = "SUCCESS"
    trace_id: str | None = None
    data: dict[str, Any] = Field(default_factory=dict)
