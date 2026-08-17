"""Agent 对外 HTTP 字段契约占位。

1. HTTP 请求只接收用户操作真正需要的 ID，不让前端提交 userId/middlewareType/metrics 等可由服务端确定的数据。
2. 用户身份来自 Gateway 已校验的 Authorization/用户上下文，不能相信 body 里的 userId。
3. HTTP 与 MQ 最终都转换为 analysis_service 的内部 AnalysisCommand。

TODO:
- [ ] Gateway JWT 完成后确定用户上下文透传方式（Authorization 或可信 X-User-Id）。
- [ ] AnalyzeResponse 与 experiment_analysis 查询 DTO 对齐。
- [ ] Patch/Compare 响应补 traceId、analysisId、versionId 等定位字段。
"""

from pydantic import BaseModel, ConfigDict, Field


class AnalyzeRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="forbid")

    task_id: int = Field(alias="taskId")
    baseline_task_id: int | None = Field(default=None, alias="baselineTaskId")


class PatchRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="forbid")

    analysis_id: int = Field(alias="analysisId")


class CompareRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="forbid")

    before_task_id: int = Field(alias="beforeTaskId")
    after_task_id: int = Field(alias="afterTaskId")
