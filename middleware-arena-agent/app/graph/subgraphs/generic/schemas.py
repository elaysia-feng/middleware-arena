"""通用专家结构化输出模型。"""

from pydantic import BaseModel, Field


class GenericHypothesisOutput(BaseModel):
    """通用专家在缺少专项类型时生成的保守假设。"""

    type: str = Field(min_length=1, max_length=80)
    title: str = Field(min_length=1, max_length=120)
    summary: str = Field(min_length=1, max_length=600)
    confidence: float = Field(ge=0, le=1)
    evidence_ids: list[str] = Field(min_length=1, max_length=8)
    verification: list[str] = Field(default_factory=list, max_length=5)
    suggestions: list[str] = Field(default_factory=list, max_length=5)


class GenericDiagnosisOutput(BaseModel):
    """通用专家节点的结构化诊断结果。"""

    summary: str = Field(min_length=1, max_length=800)
    hypotheses: list[GenericHypothesisOutput] = Field(default_factory=list, max_length=5)
    limitations: list[str] = Field(default_factory=list, max_length=5)
