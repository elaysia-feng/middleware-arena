"""RabbitMQ Pydantic message contracts shared with Java services."""

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class AgentAnalysisTaskMessage(BaseModel):
    """experiment-service -> agent-service 自动分析任务。"""

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
    """agent-service -> experiment-service 分析状态/结果。"""

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
