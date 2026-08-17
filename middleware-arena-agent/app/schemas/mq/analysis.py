"""Agent RabbitMQ Message 模型。

1. `*Message` 只描述 Java/Python 之间的 RabbitMQ JSON 契约。
2. 字段名通过 Pydantic alias 对齐 Java Jackson 默认 camelCase。
3. MQ Message 收到后必须转换为 AnalysisCommand，不直接作为 LangGraph State 使用。
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
    """agent-service -> experiment-service 的状态/结果消息。"""

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
