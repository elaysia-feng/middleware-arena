"""Agent -> Experiment 的分析状态/结果 MQ 消息。"""

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


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
