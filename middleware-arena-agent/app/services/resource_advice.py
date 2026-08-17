"""Resource-advice business logic."""

import math
from statistics import quantiles

from app.clients.llm import request_resource_advice
from app.schemas.resource import (
    AdviceDetail,
    ResourceAdviceRequest,
    ResourceAdviceResponse,
    ResourceBudget,
)


def calculate_resource_advice(request: ResourceAdviceRequest) -> ResourceAdviceResponse:
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
