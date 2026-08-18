"""资源建议业务逻辑。

这个 Service 做的是“多种建议来源的业务合并”，而不是单纯调用 LLM：

规则预算      -> 平台给出的最低安全值
历史 P95      -> 根据真实运行情况估算通常需要多少资源
LLM 建议      -> 辅助参考
最大预算      -> 平台硬上限
       ↓
最终预算

LLM 只是其中一个可降级来源，因此即使 LLM 调用失败，接口仍然可以返回规则/历史结果。
"""

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
    """根据规则、历史和 LLM 计算最终资源预算。

    执行顺序：
    1. 从历史实验计算 P95 资源使用量。
    2. 请求 LLM 给出辅助建议；失败时回退到规则预算。
    3. 三种建议取较大值，避免资源给得太少。
    4. 再用 max_budget 截断，避免超出平台允许上限。
    """

    # ------------------------------------------------------------------
    # 1. 历史数据建议
    # ------------------------------------------------------------------
    if len(request.history) >= 2:
        # quantiles(..., n=20) 把数据切成 20 个等分点；下标 18 对应 95% 分位附近。
        # 使用 P95 而不是 max：max 容易被一次异常尖峰拉得过高；平均值又可能低估高峰。
        cpu_p95 = quantiles(
            [item.cpu_cores for item in request.history],
            n=20,
            method="inclusive",
        )[18]
        memory_p95 = quantiles(
            [item.memory_mb for item in request.history],
            n=20,
            method="inclusive",
        )[18]

        # P95 后再乘 safety_factor，例如 1.2 表示预留 20% 安全余量。
        history_budget = AdviceDetail(
            cpus=round(cpu_p95 * request.safety_factor, 2),
            # 内存必须是整数 MB，所以向上取整，宁可多 1MB 也不要因为截断造成不足。
            memory_mb=math.ceil(memory_p95 * request.safety_factor),
            reason=f"最近 {len(request.history)} 个同类实验 P95 × {request.safety_factor}",
        )
    else:
        # 样本太少时 P95 没有统计意义，直接回退到平台规则预算。
        history_budget = AdviceDetail(
            cpus=request.rule_budget.cpus,
            memory_mb=request.rule_budget.memory_mb,
            reason="历史样本不足，使用规则预算",
        )

    # ------------------------------------------------------------------
    # 2. LLM 辅助建议
    # ------------------------------------------------------------------
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
        # LLM 是辅助能力，不应该因为 Key 没配置、超时、限流就让整个接口 500。
        # fallback 放在 Service 而不是 Client，是因为“失败后用什么值”属于业务决策。
        llm_budget = AdviceDetail(
            cpus=request.rule_budget.cpus,
            memory_mb=request.rule_budget.memory_mb,
            reason=f"LLM 不可用，使用规则预算：{type(error).__name__}",
        )

    # ------------------------------------------------------------------
    # 3. 汇总最终预算
    # ------------------------------------------------------------------
    final_budget = ResourceBudget(
        cpus=min(
            # 先取三个建议的最大值，保证不会因为某一路估算过低造成 OOM/CPU 不足；
            # 再通过 min 限制在平台 max_budget 以内。
            request.max_budget.cpus,
            max(
                request.rule_budget.cpus,
                history_budget.cpus,
                llm_budget.cpus,
            ),
        ),
        memory_mb=min(
            request.max_budget.memory_mb,
            max(
                request.rule_budget.memory_mb,
                history_budget.memory_mb,
                llm_budget.memory_mb,
            ),
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
