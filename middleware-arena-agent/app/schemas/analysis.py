"""分析域使用的 Pydantic 数据模型。

为什么都继承 ``BaseModel``：
1. ``BaseModel`` 会在创建对象时做类型校验，例如 taskId 传成负数会直接报错。
2. 可以通过 ``model_dump()`` / ``model_dump_json()`` 做序列化，方便 HTTP 和 MQ 传输。
3. FastAPI 会读取这些模型自动生成 OpenAPI / Swagger 接口文档。

这里按 Python/FastAPI 常见方式按“业务域”放模型，而不是再拆 Java 风格的 DTO/VO/PO 目录。

``ConfigDict`` 中几个常用配置：
- ``populate_by_name=True``：既允许使用 Python 字段名 ``task_id``，也允许使用 alias ``taskId`` 创建对象。
- ``extra='forbid'``：请求里出现未定义字段时直接拒绝，避免前端偷偷传 metrics/userId 等不可信字段。
"""

from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field


class AnalyzeRequest(BaseModel):
    """POST /agent/analyze 的 HTTP 请求体。

    HTTP 主动分析只要求前端告诉 Agent“分析哪一次实验”；
    versionId、userId、middlewareType 等可信信息后续由服务端自己查询，不直接信任前端。
    """

    model_config = ConfigDict(populate_by_name=True, extra="forbid")

    task_id: int = Field(
        alias="taskId",
        gt=0,
        description="要分析的 experiment_task.id，必须大于 0",
    )
    baseline_task_id: int | None = Field(
        default=None,
        alias="baselineTaskId",
        gt=0,
        description="可选的基线实验任务 ID，用于当前结果和历史结果对比",
    )


class AnalyzeResponse(BaseModel):
    """POST /agent/analyze 的 HTTP 响应体。"""

    model_config = ConfigDict(populate_by_name=True)

    task_id: int = Field(alias="taskId", description="本次被分析的实验任务 ID")
    analysis_id: int | None = Field(
        default=None,
        alias="analysisId",
        description="experiment_analysis.id；分析任务尚未持久化时可能为空",
    )
    status: str = Field(description="分析状态，例如 SUCCESS / FAILED / ANALYZING")
    trace_id: str | None = Field(
        default=None,
        alias="traceId",
        description="Langfuse Trace ID，用于定位本次 Agent 执行链路",
    )
    result: dict[str, Any] = Field(
        default_factory=dict,
        description="分析结果数据；后续可继续拆成 Evidence/Hypothesis/Bottleneck 等结构化模型",
    )


class PatchRequest(BaseModel):
    """POST /agent/patch 的请求体。

    Patch 必须基于已经存在的 analysisId 生成，避免客户端自己拼接瓶颈信息。
    """

    model_config = ConfigDict(populate_by_name=True, extra="forbid")

    analysis_id: int = Field(
        alias="analysisId",
        gt=0,
        description="作为 Patch 依据的 experiment_analysis.id",
    )


class PatchResponse(BaseModel):
    """Agent 生成候选 Patch 后返回给前端的响应骨架。"""

    model_config = ConfigDict(populate_by_name=True)

    analysis_id: int = Field(alias="analysisId", description="Patch 对应的分析任务 ID")
    patch_id: int | None = Field(
        default=None,
        alias="patchId",
        description="experiment_patch.id；未持久化时可能为空",
    )
    status: str = Field(description="Patch 状态，例如 CREATED / ACCEPTED / REJECTED / APPLIED")

    # TODO[Agent Core - 由你实现]:
    # 1. files：具体修改了哪些文件。
    # 2. summary：为什么要这样修改。
    # 3. validation：静态检查/构建验证结果。


class CompareRequest(BaseModel):
    """POST /agent/compare 的请求体，用于比较优化前后的两次压测。"""

    model_config = ConfigDict(populate_by_name=True, extra="forbid")

    before_task_id: int = Field(
        alias="beforeTaskId",
        gt=0,
        description="优化前的 experiment_task.id",
    )
    after_task_id: int = Field(
        alias="afterTaskId",
        gt=0,
        description="应用 Patch 并重新压测后的 experiment_task.id",
    )


class CompareResponse(BaseModel):
    """优化前后实验对比结果。"""

    model_config = ConfigDict(populate_by_name=True)

    before_task_id: int = Field(alias="beforeTaskId", description="优化前任务 ID")
    after_task_id: int = Field(alias="afterTaskId", description="优化后任务 ID")
    status: str = Field(description="比较流程执行状态")
    result: dict[str, Any] = Field(
        default_factory=dict,
        description="QPS/P95/Error/CPU/Memory 的变化以及最终 verdict",
    )

    # TODO[Agent Core - 由你实现]:
    # 将 result 从 dict 继续细化成 CompareMetrics / CompareVerdict 等模型。


class AnalysisCommand(BaseModel):
    """HTTP 和 MQ 进入业务层后统一使用的内部命令对象。

    为什么还需要 Command：
    - HTTP 的 AnalyzeRequest 字段比较少；
    - MQ 的 AgentAnalysisTaskMessage 字段比较全；
    - 两种入口都先转换成同一个 AnalysisCommand，之后 Service/LangGraph 就不需要维护两套逻辑。

    这个类不是数据库 Entity，也不是直接给前端的 Request。
    """

    model_config = ConfigDict(extra="forbid")

    task_id: int = Field(description="当前要分析的实验任务 ID")
    analysis_id: int | None = Field(default=None, description="分析任务 ID；HTTP 首次触发时可能还没有")
    user_id: int | None = Field(default=None, description="任务所属用户 ID，主要用于审计、配额和权限校验")
    version_id: int | None = Field(default=None, description="本次实验实际运行的 experiment_version.id")
    baseline_task_id: int | None = Field(default=None, description="可选的对比基线任务 ID")
    middleware_type: str | None = Field(
        default=None,
        description="中间件类型，例如 REDIS / RABBITMQ / SEATA / ELASTICSEARCH，用于选择对应 SubGraph",
    )
    analysis_type: str = Field(
        default="PERFORMANCE_DIAGNOSIS",
        description="分析类型；第一版主要做性能瓶颈诊断",
    )
    trigger_type: Literal["AUTO", "MANUAL", "RETRY"] = Field(
        default="MANUAL",
        description="触发方式：AUTO=Runner 后自动触发，MANUAL=用户主动触发，RETRY=失败重试",
    )
    dispatch_id: str | None = Field(
        default=None,
        description="MQ 投递唯一标识，用于幂等；纯 HTTP 请求时可以为空",
    )


class AnalysisResult(BaseModel):
    """analysis service 的协议无关返回值。

    Service 只返回这个对象：
    - HTTP 层再把它转成 AnalyzeResponse；
    - MQ Consumer 再把它转成 AgentAnalysisStatusMessage。
    这样业务层不会依赖具体传输协议。
    """

    analysis_id: int | None = Field(default=None, description="本次分析任务 ID")
    task_id: int = Field(description="被分析的实验任务 ID")
    status: str = Field(default="SUCCESS", description="业务执行结果状态")
    trace_id: str | None = Field(default=None, description="Langfuse Trace ID")
    data: dict[str, Any] = Field(
        default_factory=dict,
        description="LangGraph 最终输出；后续由你逐步替换为更明确的结构化结果",
    )


class AnalysisContext(BaseModel):
    """experiment-service 返回给 load_context 节点的可信实验上下文。"""

    model_config = ConfigDict(populate_by_name=True, extra="forbid")

    task_id: int = Field(alias="taskId", gt=0)
    user_id: int = Field(alias="userId", gt=0)
    version_id: int = Field(alias="versionId", gt=0)
    baseline_task_id: int | None = Field(default=None, alias="baselineTaskId", gt=0)
    middleware_type: str = Field(alias="middlewareType", min_length=1)
    config: dict[str, Any] = Field(default_factory=dict)
    files: list[dict[str, Any]] = Field(default_factory=list)
    code_diff: list[dict[str, Any]] = Field(default_factory=list, alias="codeDiff")
    metrics: dict[str, Any] = Field(default_factory=dict)
    baseline_metrics: dict[str, Any] = Field(default_factory=dict, alias="baselineMetrics")
    logs: list[str] = Field(default_factory=list)


class SimilarExperiment(BaseModel):
    """experiment-service 返回的一条历史相似实验。"""

    model_config = ConfigDict(populate_by_name=True, extra="forbid")

    task_id: int = Field(alias="taskId", gt=0)
    version_id: int = Field(alias="versionId", gt=0)
    middleware_type: str = Field(alias="middlewareType", min_length=1)
    scenario: str | None = None
    similarity_score: float = Field(alias="similarityScore", ge=0, le=1)
    metrics: dict[str, Any] = Field(default_factory=dict)
