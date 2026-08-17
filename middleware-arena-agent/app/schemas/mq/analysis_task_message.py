"""Experiment -> Agent 的分析任务 MQ 消息。"""

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
