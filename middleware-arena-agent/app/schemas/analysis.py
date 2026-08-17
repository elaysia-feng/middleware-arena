"""Agent 对外 HTTP 字段契约。

1. HTTP 请求只接收用户操作需要的 ID，不信任前端提交的 userId/metrics/middlewareType。
2. 用户身份后续由 Gateway 鉴权上下文透传。
3. HTTP 与 MQ 都转换为 analysis_service.AnalysisCommand。

TODO:
- [ ] Gateway JWT 完成后确定可信用户上下文 Header。
- [ ] Patch/Compare 的成功响应在核心逻辑完成后补齐业务字段。
"""

from typing import Any

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
    result: dict[str, Any] | None = None
    trace_id: str | None = Field(default=None, alias="traceId")


class PatchRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="forbid")

    analysis_id: int = Field(alias="analysisId", gt=0)


class CompareRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="forbid")

    before_task_id: int = Field(alias="beforeTaskId", gt=0)
    after_task_id: int = Field(alias="afterTaskId", gt=0)
