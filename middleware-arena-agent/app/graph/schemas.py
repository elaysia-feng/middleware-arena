"""主图中 LLM 节点使用的结构化输出模型。"""

from typing import Literal

from pydantic import BaseModel, Field


class GeneratedHypothesis(BaseModel):
    """经过 evidenceId 约束的单条候选瓶颈假设。"""

    type: str = Field(min_length=1, max_length=80)
    title: str = Field(min_length=1, max_length=120)
    summary: str = Field(min_length=1, max_length=600)
    confidence: float = Field(ge=0, le=1)
    evidence_ids: list[str] = Field(min_length=1, max_length=10)
    verification: list[str] = Field(default_factory=list, max_length=5)
    suggestions: list[str] = Field(default_factory=list, max_length=5)


class HypothesisGenerationOutput(BaseModel):
    """假设生成节点要求 LLM 返回的完整结构。"""

    summary: str = Field(min_length=1, max_length=800)
    hypotheses: list[GeneratedHypothesis] = Field(default_factory=list, max_length=8)
    limitations: list[str] = Field(default_factory=list, max_length=5)


class BottleneckJudgeOutput(BaseModel):
    """证据裁决节点的状态、主次假设和置信度输出。"""

    status: Literal["CONFIRMED", "UNCERTAIN", "INSUFFICIENT_EVIDENCE"]
    primary_hypothesis_id: str | None = None
    secondary_hypothesis_ids: list[str] = Field(default_factory=list, max_length=3)
    confidence: float = Field(ge=0, le=1)
    reason: str = Field(min_length=1, max_length=800)
    evidence_ids: list[str] = Field(default_factory=list, max_length=12)
    suggestions: list[str] = Field(default_factory=list, max_length=8)
    limitations: list[str] = Field(default_factory=list, max_length=5)


class GeneratedPatch(BaseModel):
    """模型生成的单个候选文件修改。"""

    path: str = Field(min_length=1, max_length=500)
    summary: str = Field(min_length=1, max_length=500)
    unified_diff: str = Field(min_length=1, max_length=30000)
    evidence_ids: list[str] = Field(default_factory=list, max_length=10)
    risks: list[str] = Field(default_factory=list, max_length=5)


class PatchGenerationOutput(BaseModel):
    """候选 Patch 节点的结构化输出。"""

    summary: str = Field(min_length=1, max_length=800)
    patches: list[GeneratedPatch] = Field(default_factory=list, max_length=8)
    validation_steps: list[str] = Field(default_factory=list, max_length=8)
    limitations: list[str] = Field(default_factory=list, max_length=5)


class ReportGenerationOutput(BaseModel):
    """最终报告节点的结构化输出。"""

    title: str = Field(min_length=1, max_length=120)
    summary: str = Field(min_length=1, max_length=1000)
    markdown: str = Field(min_length=1, max_length=30000)
