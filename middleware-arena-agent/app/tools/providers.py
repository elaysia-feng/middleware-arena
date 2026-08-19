"""Agent Tool 的底层能力协议。

Tool 本身只负责把“模型参数”转换为受控请求；真正的数据来源由 Provider 决定。
后续可以分别接：
- Redis / MySQL / RabbitMQ 诊断服务；
- experiment-service 内部 HTTP API；
- Qdrant / Elasticsearch 相似实验检索；
- 官方文档 / 内部文档知识库。

这样 Tool Schema 不会因为底层存储从 HTTP 换成 Prometheus、Qdrant 等而变化。
"""

from typing import Protocol

from app.tools.tool_schemas import (
    KnowledgeBaseSearchInput,
    MySqlDiagnosticsInput,
    RabbitMqDiagnosticsInput,
    RedisDiagnosticsInput,
    SimilarExperimentSearchInput,
    ToolResult,
)


class AnalysisToolProvider(Protocol):
    """5 个按需分析 Tool 对底层数据层的最小依赖接口。"""

    async def get_redis_diagnostics(self, request: RedisDiagnosticsInput) -> ToolResult:
        """获取当前实验相关 Redis 深度诊断证据。"""
        ...

    async def get_mysql_diagnostics(self, request: MySqlDiagnosticsInput) -> ToolResult:
        """获取当前实验相关 MySQL 深度诊断证据。"""
        ...

    async def get_rabbitmq_diagnostics(self, request: RabbitMqDiagnosticsInput) -> ToolResult:
        """获取当前实验相关 RabbitMQ 深度诊断证据。"""
        ...

    async def search_similar_experiments(self, request: SimilarExperimentSearchInput) -> ToolResult:
        """从历史实验库检索与当前症状相似的实验。"""
        ...

    async def search_knowledge_base(self, request: KnowledgeBaseSearchInput) -> ToolResult:
        """从技术知识库检索用于解释或优化建议的证据。"""
        ...
