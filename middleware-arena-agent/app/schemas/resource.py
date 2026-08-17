"""Pydantic models for resource-advice HTTP API."""

from pydantic import BaseModel, Field, model_validator


class ResourceBudget(BaseModel):
    cpus: float = Field(gt=0)
    memory_mb: int = Field(gt=0)


class HistoricalUsage(BaseModel):
    cpu_cores: float = Field(ge=0)
    memory_mb: int = Field(ge=0)


class ResourceAdviceRequest(BaseModel):
    experiment_type: str = Field(min_length=1)
    run_params: dict = Field(default_factory=dict)
    rule_budget: ResourceBudget
    max_budget: ResourceBudget
    history: list[HistoricalUsage] = Field(default_factory=list, max_length=200)
    safety_factor: float = Field(default=1.2, ge=1.0, le=2.0)

    @model_validator(mode="after")
    def validate_budget_range(self) -> "ResourceAdviceRequest":
        if self.rule_budget.cpus > self.max_budget.cpus:
            raise ValueError("rule_budget.cpus 不能超过 max_budget.cpus")
        if self.rule_budget.memory_mb > self.max_budget.memory_mb:
            raise ValueError("rule_budget.memory_mb 不能超过 max_budget.memory_mb")
        return self


class AdviceDetail(ResourceBudget):
    reason: str


class ResourceAdviceResponse(BaseModel):
    experiment_type: str
    rule_budget: ResourceBudget
    history_budget: AdviceDetail
    llm_budget: AdviceDetail
    final_budget: ResourceBudget
    llm_used: bool
