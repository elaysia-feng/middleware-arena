"""Redis 专家 SubGraph 的结构化 LLM 输出模型。"""

from pydantic import BaseModel, Field


class RedisHypothesisOutput(BaseModel):
    """单个 Redis 候选假设。"""

    type: str = Field(min_length=1, max_length=80)
    title: str = Field(min_length=1, max_length=120)
    summary: str = Field(min_length=1, max_length=600)
    confidence: float = Field(ge=0, le=1)
    evidence_ids: list[str] = Field(min_length=1, max_length=8)
    verification: list[str] = Field(default_factory=list, max_length=5)
    suggestions: list[str] = Field(default_factory=list, max_length=5)


class RedisDiagnosisOutput(BaseModel):
    """Redis 专家一次分析的完整输出。"""

    summary: str = Field(min_length=1, max_length=800)
    hypotheses: list[RedisHypothesisOutput] = Field(default_factory=list, max_length=5)
    limitations: list[str] = Field(default_factory=list, max_length=5)
