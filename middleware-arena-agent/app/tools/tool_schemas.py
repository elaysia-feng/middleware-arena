"""Agent Tool 的输入/输出 Schema。

设计原则：
1. 每次分析都需要的实验结果、baseline、配置、基础日志摘要由 LangGraph Node 直接加载，不做 Tool。
2. Tool 只保留“Agent 判断有必要时才继续深挖”的诊断与检索能力。
3. 所有输入都通过 Pydantic 限制范围，避免把任意 SQL、Shell、Redis Command 直接暴露给模型。
"""

from typing import Any, Literal

from pydantic import BaseModel, Field


MiddlewareType = Literal["redis", "mysql", "rabbitmq", "jvm", "general"]
ToolStatus = Literal["ok", "partial", "unavailable"]
KnowledgeSourceType = Literal["official", "internal", "postmortem", "experiment"]


class ToolResult(BaseModel):
    """所有分析 Tool 统一返回结构。

    ``summary`` 给模型快速阅读；``data`` 放结构化指标；``evidence`` 放关键证据；
    ``warnings`` 用于声明采样、缺失数据等限制，避免模型把不完整结果当成确定事实。
    """

    status: ToolStatus = "ok"
    summary: str
    data: dict[str, Any] = Field(default_factory=dict)
    evidence: list[str] = Field(default_factory=list)
    warnings: list[str] = Field(default_factory=list)


class RedisDiagnosticsInput(BaseModel):
    """Redis 深度诊断参数。"""

    task_id: int = Field(gt=0, description="当前实验任务 ID")
    include_big_keys: bool = Field(default=True, description="是否检查 BigKey/热点 Key 证据")
    include_slowlog: bool = Field(default=True, description="是否读取 Redis SlowLog 摘要")
    max_big_keys: int = Field(default=10, ge=1, le=20, description="最多返回多少个可疑 BigKey")
    max_slowlog_entries: int = Field(default=20, ge=1, le=50, description="最多返回多少条 SlowLog")


class MySqlDiagnosticsInput(BaseModel):
    """MySQL 深度诊断参数。

    注意：这里故意没有 ``sql`` 参数。Agent 不拥有任意 SQL 执行能力，只能请求受控诊断数据。
    """

    task_id: int = Field(gt=0, description="当前实验任务 ID")
    include_slow_queries: bool = Field(default=True, description="是否返回慢 SQL 摘要")
    include_connection_stats: bool = Field(default=True, description="是否返回连接/连接池指标")
    max_slow_queries: int = Field(default=10, ge=1, le=20, description="最多返回多少条慢 SQL")


class RabbitMqDiagnosticsInput(BaseModel):
    """RabbitMQ 深度诊断参数。"""

    task_id: int = Field(gt=0, description="当前实验任务 ID")
    queue_names: list[str] = Field(
        default_factory=list,
        max_length=10,
        description="只关注这些队列；空数组表示由后端根据 task 自动确定相关队列",
    )
    include_rates: bool = Field(default=True, description="是否返回 publish/deliver/ack 等速率")
    max_queues: int = Field(default=10, ge=1, le=20, description="最多返回多少个队列")


class SimilarExperimentSearchInput(BaseModel):
    """历史相似实验检索参数。"""

    query: str = Field(
        min_length=3,
        max_length=500,
        description="基于当前症状生成的检索文本，例如 Redis timeout + p95 上升 + 大 value",
    )
    middleware: MiddlewareType | None = Field(default=None, description="可选中间件过滤")
    top_k: int = Field(default=5, ge=1, le=10, description="最多返回多少个历史实验")
    exclude_task_id: int | None = Field(default=None, gt=0, description="排除当前任务，避免检索到自己")


class KnowledgeBaseSearchInput(BaseModel):
    """技术知识库检索参数。"""

    query: str = Field(min_length=3, max_length=500, description="需要查证的技术问题或优化方向")
    middleware: MiddlewareType | None = Field(default=None, description="可选中间件过滤")
    source_types: list[KnowledgeSourceType] = Field(
        default_factory=list,
        max_length=4,
        description="来源过滤；空数组表示不限制，可选 official/internal/postmortem/experiment",
    )
    top_k: int = Field(default=5, ge=1, le=10, description="最多返回多少条知识片段")
