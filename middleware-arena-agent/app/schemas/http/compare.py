"""Compare HTTP 请求/响应模型。"""

from typing import Any

from pydantic import BaseModel, ConfigDict, Field


class CompareRequest(BaseModel):
    """POST /agent/compare 请求体。"""

    model_config = ConfigDict(populate_by_name=True, extra="forbid")

    before_task_id: int = Field(alias="beforeTaskId", gt=0)
    after_task_id: int = Field(alias="afterTaskId", gt=0)


class CompareResponse(BaseModel):
    """POST /agent/compare 响应骨架。"""

    model_config = ConfigDict(populate_by_name=True)

    before_task_id: int = Field(alias="beforeTaskId")
    after_task_id: int = Field(alias="afterTaskId")
    status: str
    result: dict[str, Any] = Field(default_factory=dict)

    # TODO[Agent Core]: 后续补 qps/p95/errorRate delta 与 verdict。
