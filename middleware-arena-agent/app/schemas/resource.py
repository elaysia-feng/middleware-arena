"""资源建议接口使用的 Pydantic 模型。

这一组模型用于 ``POST /agent/resource/advice``。
Pydantic 的 ``Field`` 不只是做校验：``description`` 还会进入 FastAPI 自动生成的
OpenAPI/Swagger 文档，所以字段含义尽量直接写在模型上。
"""

from pydantic import BaseModel, Field, model_validator


class ResourceBudget(BaseModel):
    """一次实验允许使用的 CPU / 内存预算。"""

    cpus: float = Field(
        gt=0,
        description="CPU 核数，例如 0.5 / 1 / 2；必须大于 0",
    )
    memory_mb: int = Field(
        gt=0,
        description="内存预算，单位 MB，例如 1024 表示 1GB",
    )


class HistoricalUsage(BaseModel):
    """一次历史实验实际观测到的资源使用量。"""

    cpu_cores: float = Field(
        ge=0,
        description="历史实验实际使用的 CPU 核数；允许为 0",
    )
    memory_mb: int = Field(
        ge=0,
        description="历史实验实际使用内存，单位 MB",
    )


class ResourceAdviceRequest(BaseModel):
    """资源建议请求。

    计算时会同时参考：规则最低预算、历史使用量、LLM 建议、最大预算。
    最终不会低于规则预算，也不会突破 max_budget。
    """

    experiment_type: str = Field(
        min_length=1,
        description="实验类型/模板类型，例如 REDIS / RABBITMQ",
    )
    run_params: dict = Field(
        default_factory=dict,
        description="本次实验运行参数；具体字段由实验模板决定",
    )
    rule_budget: ResourceBudget = Field(
        description="平台规则计算出的最低安全预算；最终结果不能低于它"
    )
    max_budget: ResourceBudget = Field(
        description="平台允许该实验使用的最大资源上限"
    )
    history: list[HistoricalUsage] = Field(
        default_factory=list,
        max_length=200,
        description="同类实验历史资源样本，最多取 200 条用于估算 P95",
    )
    safety_factor: float = Field(
        default=1.2,
        ge=1.0,
        le=2.0,
        description="历史 P95 的安全系数；1.2 表示在 P95 基础上额外预留 20%",
    )

    @model_validator(mode="after")
    def validate_budget_range(self) -> "ResourceAdviceRequest":
        """跨字段校验：规则最低预算不能反过来超过最大预算。

        ``Field`` 适合校验单个字段；这里需要同时比较 rule_budget 和 max_budget，
        所以使用 ``model_validator(mode='after')`` 在整个模型解析完成后再检查。
        """
        if self.rule_budget.cpus > self.max_budget.cpus:
            raise ValueError("rule_budget.cpus 不能超过 max_budget.cpus")
        if self.rule_budget.memory_mb > self.max_budget.memory_mb:
            raise ValueError("rule_budget.memory_mb 不能超过 max_budget.memory_mb")
        return self


class AdviceDetail(ResourceBudget):
    """带解释信息的资源建议。

    直接继承 ``ResourceBudget``，因此自动拥有 cpus / memory_mb 两个字段，
    再补一个 reason，避免重复定义相同字段。
    """

    reason: str = Field(description="本次预算是如何计算/推荐出来的")


class ResourceAdviceResponse(BaseModel):
    """资源建议接口最终响应。"""

    experiment_type: str = Field(description="规范化后的实验类型")
    rule_budget: ResourceBudget = Field(description="规则最低预算")
    history_budget: AdviceDetail = Field(description="根据历史 P95 计算出的预算")
    llm_budget: AdviceDetail = Field(description="LLM 给出的辅助建议；失败时会回退到规则预算")
    final_budget: ResourceBudget = Field(description="综合规则、历史和 LLM 后得到的最终预算")
    llm_used: bool = Field(description="本次是否成功使用了 LLM 建议")
