"""实验资源建议接口模型与计算逻辑。"""

import math
from statistics import quantiles

from pydantic import BaseModel, Field, model_validator

from app.services.llm import request_resource_advice


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


def calculate_resource_advice(request: ResourceAdviceRequest) -> ResourceAdviceResponse:
    # 1. 历史样本使用 P95，并乘安全系数；样本不足时退回规则预算。
    if len(request.history) >= 2:
        cpu_p95 = quantiles([item.cpu_cores for item in request.history], n=20, method="inclusive")[18]
        memory_p95 = quantiles([item.memory_mb for item in request.history], n=20, method="inclusive")[18]
        history_budget = AdviceDetail(
            cpus=round(cpu_p95 * request.safety_factor, 2),
            memory_mb=math.ceil(memory_p95 * request.safety_factor),
            reason=f"最近 {len(request.history)} 个同类实验 P95 × {request.safety_factor}",
        )
    else:
        history_budget = AdviceDetail(
            cpus=request.rule_budget.cpus,
            memory_mb=request.rule_budget.memory_mb,
            reason="历史样本不足，使用规则预算",
        )

    # 2. LLM 只提供建议；未配置或调用失败时明确回退，不影响规则服务可用性。
    llm_used = False
    try:
        llm_result = request_resource_advice(
            {
                "experimentType": request.experiment_type,
                "runParams": request.run_params,
                "ruleBudget": request.rule_budget.model_dump(),
                "historyBudget": history_budget.model_dump(),
                "maxBudget": request.max_budget.model_dump(),
            }
        )
        llm_budget = AdviceDetail(
            cpus=llm_result["cpus"],
            memory_mb=llm_result["memory_mb"],
            reason=llm_result["reason"],
        )
        llm_used = True
    except Exception as error:
        llm_budget = AdviceDetail(
            cpus=request.rule_budget.cpus,
            memory_mb=request.rule_budget.memory_mb,
            reason=f"LLM 不可用，使用规则预算：{type(error).__name__}",
        )

    # 3. 分别取规则、历史和 LLM 建议的最大值，并限制在实验允许的最大预算内。
    final_budget = ResourceBudget(
        cpus=min(
            request.max_budget.cpus,
            max(request.rule_budget.cpus, history_budget.cpus, llm_budget.cpus),
        ),
        memory_mb=min(
            request.max_budget.memory_mb,
            max(request.rule_budget.memory_mb, history_budget.memory_mb, llm_budget.memory_mb),
        ),
    )
    return ResourceAdviceResponse(
        experiment_type=request.experiment_type.upper(),
        rule_budget=request.rule_budget,
        history_budget=history_budget,
        llm_budget=llm_budget,
        final_budget=final_budget,
        llm_used=llm_used,
    )
