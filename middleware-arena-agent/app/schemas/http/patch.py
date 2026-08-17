"""Patch HTTP 请求/响应模型。"""

from pydantic import BaseModel, ConfigDict, Field


class PatchRequest(BaseModel):
    """POST /agent/patch 请求体。"""

    model_config = ConfigDict(populate_by_name=True, extra="forbid")

    analysis_id: int = Field(alias="analysisId", gt=0)


class PatchResponse(BaseModel):
    """POST /agent/patch 响应骨架。"""

    model_config = ConfigDict(populate_by_name=True)

    analysis_id: int = Field(alias="analysisId")
    patch_id: int | None = Field(default=None, alias="patchId")
    status: str

    # TODO[Agent Core]: 后续补 files / summary / validation 等业务字段。
