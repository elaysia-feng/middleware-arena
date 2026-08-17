"""Agent RabbitMQ JSON 消息契约。

1. MQ 只传任务标识和路由所需元数据，不传完整代码、metrics、日志等大字段。
2. 字段名使用 Java Jackson 默认 camelCase；Python 通过 Pydantic alias 映射到 snake_case。
3. analysisId + dispatchId 用于幂等；taskId/versionId 用于 Agent 再通过 HTTP Tool 拉取真实实验上下文。

TODO:
- [ ] consumer 收到消息后先按 analysisId / dispatchId 做幂等校验。
- [ ] baselineTaskId 为空时，由 load_context 根据当前实验策略寻找 baseline。
- [ ] status/result 消息字段最终与 experiment_analysis 表结构一起定稿。
"""

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class AgentAnalysisTaskMessage(BaseModel):
    """experiment-service -> agent-service 的自动分析任务。"""

    model_config = ConfigDict(populate_by_name=True, extra="forbid")

    analysis_id: int = Field(alias="analysisId")
    task_id: int = Field(alias="taskId")
    user_id: int = Field(alias="userId")
    version_id: int = Field(alias="versionId")
    baseline_task_id: int | None = Field(default=None, alias="baselineTaskId")
    middleware_type: str = Field(alias="middlewareType")
    analysis_type: Literal["PERFORMANCE_DIAGNOSIS"] = Field(
        default="PERFORMANCE_DIAGNOSIS", alias="analysisType"
    )
    trigger_type: Literal["AUTO", "MANUAL", "RETRY"] = Field(alias="triggerType")
    dispatch_id: str = Field(alias="dispatchId")
    queued_at_epoch_ms: int = Field(alias="queuedAtEpochMs")


class AgentAnalysisStatusMessage(BaseModel):
    """agent-service -> experiment-service 的状态/结果回传契约占位。"""

    model_config = ConfigDict(populate_by_name=True, extra="forbid")

    analysis_id: int = Field(alias="analysisId")
    task_id: int = Field(alias="taskId")
    status: Literal["ANALYZING", "SUCCESS", "FAILED"]
    current_stage: str | None = Field(default=None, alias="currentStage")
    progress: int | None = None
    result_json: str | None = Field(default=None, alias="resultJson")
    error_code: str | None = Field(default=None, alias="errorCode")
    error_message: str | None = Field(default=None, alias="errorMessage")
    finished_at_epoch_ms: int | None = Field(default=None, alias="finishedAtEpochMs")
