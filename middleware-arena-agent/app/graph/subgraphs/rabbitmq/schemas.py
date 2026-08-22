"""RabbitMQ 专家结构化输出模型。"""

from pydantic import BaseModel, Field


class RabbitMqHypothesisOutput(BaseModel):
    """RabbitMQ 专家生成的单条可验证假设。"""

    type: str = Field(min_length=1, max_length=80)
    title: str = Field(min_length=1, max_length=120)
    summary: str = Field(min_length=1, max_length=600)
    confidence: float = Field(ge=0, le=1)
    evidence_ids: list[str] = Field(min_length=1, max_length=8)
    verification: list[str] = Field(default_factory=list, max_length=5)
    suggestions: list[str] = Field(default_factory=list, max_length=5)


class RabbitMqDiagnosisOutput(BaseModel):
    """RabbitMQ 专家节点的结构化诊断结果。"""

    summary: str = Field(min_length=1, max_length=800)
    hypotheses: list[RabbitMqHypothesisOutput] = Field(default_factory=list, max_length=5)
    limitations: list[str] = Field(default_factory=list, max_length=5)
