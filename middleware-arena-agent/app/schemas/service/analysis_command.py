"""Analysis Service Command 模型。"""

from typing import Literal

from pydantic import BaseModel, ConfigDict


class AnalysisCommand(BaseModel):
    """HTTP Request / MQ Message 转换后的 Service 内部命令。"""

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
