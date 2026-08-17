"""RabbitMQ 跨服务消息模型。

这里的模型不是普通业务对象，而是 Java experiment-service 与 Python agent-service
之间必须保持一致的 JSON 契约。

为什么也继承 ``BaseModel``：
1. Consumer 收到字节消息后可直接 ``model_validate_json`` 校验格式。
2. Publisher 可以 ``model_dump_json(by_alias=True)`` 输出 Java 熟悉的 camelCase 字段。
3. ``extra='forbid'`` 可以尽早发现 Java/Python 字段契约漂移，而不是静默忽略错误字段。

注意：修改这里的字段时，Java 端 ``AgentAnalysisTaskMessage`` /
``AgentAnalysisStatusMessage`` 也要同步修改。
"""

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class AgentAnalysisTaskMessage(BaseModel):
    """experiment-service -> agent-service 的自动分析任务消息。

    MQ 只传轻量 ID 和调度信息，不传完整代码、metricsJson、日志等大字段。
    Agent 收到这些 ID 后，再通过 Experiment Internal API 拉取真正分析所需数据。
    """

    model_config = ConfigDict(populate_by_name=True, extra="forbid")

    analysis_id: int = Field(alias="analysisId", description="experiment_analysis.id，也是分析任务主幂等键")
    task_id: int = Field(alias="taskId", description="本次需要分析的 experiment_task.id")
    user_id: int = Field(alias="userId", description="任务所属用户 ID，用于审计和配额")
    version_id: int = Field(alias="versionId", description="本次压测实际执行的 experiment_version.id")
    baseline_task_id: int | None = Field(
        default=None,
        alias="baselineTaskId",
        description="可选基线任务 ID；用于和当前实验做前后对比",
    )
    middleware_type: str = Field(
        alias="middlewareType",
        description="中间件类型，用于 LangGraph Router 选择 Redis/RabbitMQ/Seata/ES SubGraph",
    )
    analysis_type: Literal["PERFORMANCE_DIAGNOSIS"] = Field(
        default="PERFORMANCE_DIAGNOSIS",
        alias="analysisType",
        description="分析类型；第一版固定为性能诊断",
    )
    trigger_type: Literal["AUTO", "MANUAL", "RETRY"] = Field(
        alias="triggerType",
        description="触发方式；自动压测完成、人工触发或重试",
    )
    dispatch_id: str = Field(
        alias="dispatchId",
        description="本次 MQ 投递唯一 ID；防止同一个 analysisId 因重复投递而重复执行",
    )
    queued_at_epoch_ms: int = Field(
        alias="queuedAtEpochMs",
        description="消息进入队列的毫秒时间戳，可用于计算排队耗时和超时",
    )


class AgentAnalysisStatusMessage(BaseModel):
    """agent-service -> experiment-service 的分析状态/结果消息。

    Agent 在开始、成功和失败时都可以发布本消息，Java 端消费后更新
    ``experiment_analysis`` 的状态和最终结果。
    """

    model_config = ConfigDict(populate_by_name=True, extra="forbid")

    analysis_id: int = Field(alias="analysisId", description="要更新的 experiment_analysis.id")
    task_id: int = Field(alias="taskId", description="对应的 experiment_task.id，方便关联和日志定位")
    status: Literal["ANALYZING", "SUCCESS", "FAILED"] = Field(
        description="Agent 当前状态：分析中、成功或失败"
    )
    current_stage: str | None = Field(
        default=None,
        alias="currentStage",
        description="当前 LangGraph 阶段，例如 LOAD_CONTEXT / METRICS / JUDGE / REPORT",
    )
    progress: int | None = Field(
        default=None,
        ge=0,
        le=100,
        description="粗粒度进度百分比，0~100；主要给前端展示",
    )
    result_json: str | None = Field(
        default=None,
        alias="resultJson",
        description="成功后的结构化结果 JSON；后续过大时应改成 OSS object key",
    )
    error_code: str | None = Field(
        default=None,
        alias="errorCode",
        description="失败错误类型/错误码，例如 TimeoutError",
    )
    error_message: str | None = Field(
        default=None,
        alias="errorMessage",
        description="失败原因摘要；不要塞完整堆栈或敏感信息",
    )
    finished_at_epoch_ms: int | None = Field(
        default=None,
        alias="finishedAtEpochMs",
        description="分析结束时间的毫秒时间戳；ANALYZING 阶段通常为空",
    )
