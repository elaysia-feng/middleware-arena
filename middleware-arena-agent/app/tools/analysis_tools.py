"""Agent 按需诊断 / 检索 Tool 定义。

这里故意不放 get_experiment_result / get_baseline / get_config 等固定上下文能力：
这些数据每次分析都需要，应该由 LangGraph 的 load_context Node 直接加载，避免模型多轮 Tool Calling。

真正绑定到 LLM 时：

    tools = build_analysis_tools(provider)
    model = llm.bind_tools(tools)

Provider 负责底层数据获取，Tool 只负责受控参数和模型可见语义。
"""

from typing import Any

from langchain_core.tools import BaseTool, tool

from app.tools.providers import AnalysisToolProvider
from app.tools.tool_schemas import (
    KnowledgeBaseSearchInput,
    MySqlDiagnosticsInput,
    RabbitMqDiagnosticsInput,
    RedisDiagnosticsInput,
    SimilarExperimentSearchInput,
)


ANALYSIS_TOOL_NAMES = (
    "get_redis_diagnostics",
    "get_mysql_diagnostics",
    "get_rabbitmq_diagnostics",
    "search_similar_experiments",
    "search_knowledge_base",
)


def _dump(result: Any) -> Any:
    """把 Pydantic ToolResult 转成模型可序列化 dict。

    Provider 后续如果为了测试直接返回 dict，也允许透传，避免 Tool 层和具体实现强耦合。
    """
    if hasattr(result, "model_dump"):
        return result.model_dump(mode="json")
    return result


def build_analysis_tools(provider: AnalysisToolProvider) -> list[BaseTool]:
    """构造真正绑定给分析 Agent 的 5 个 Tool。

    1. Redis / MySQL / RabbitMQ：只有初步证据指向对应中间件时才调用。
    2. 相似实验：需要历史案例佐证当前判断时调用。
    3. 知识库：需要技术原理、官方依据或优化方案时调用。
    """

    @tool("get_redis_diagnostics", args_schema=RedisDiagnosticsInput)
    async def get_redis_diagnostics(
        task_id: int,
        include_big_keys: bool = True,
        include_slowlog: bool = True,
        max_big_keys: int = 10,
        max_slowlog_entries: int = 20,
    ) -> dict[str, Any]:
        """按需获取 Redis 深度诊断证据。

        仅当已有上下文出现 Redis timeout、延迟异常、BigKey、内存压力、热点 Key 等迹象时调用；
        不要把它作为每次实验分析的固定步骤。
        """
        request = RedisDiagnosticsInput(
            task_id=task_id,
            include_big_keys=include_big_keys,
            include_slowlog=include_slowlog,
            max_big_keys=max_big_keys,
            max_slowlog_entries=max_slowlog_entries,
        )
        return _dump(await provider.get_redis_diagnostics(request))

    @tool("get_mysql_diagnostics", args_schema=MySqlDiagnosticsInput)
    async def get_mysql_diagnostics(
        task_id: int,
        include_slow_queries: bool = True,
        include_connection_stats: bool = True,
        max_slow_queries: int = 10,
    ) -> dict[str, Any]:
        """按需获取 MySQL 深度诊断证据。

        适用于慢 SQL、连接池耗尽、DB timeout、扫描行数异常等怀疑方向。
        此 Tool 不接受 SQL 参数，也不能执行 INSERT/UPDATE/DELETE/DDL。
        """
        request = MySqlDiagnosticsInput(
            task_id=task_id,
            include_slow_queries=include_slow_queries,
            include_connection_stats=include_connection_stats,
            max_slow_queries=max_slow_queries,
        )
        return _dump(await provider.get_mysql_diagnostics(request))

    @tool("get_rabbitmq_diagnostics", args_schema=RabbitMqDiagnosticsInput)
    async def get_rabbitmq_diagnostics(
        task_id: int,
        queue_names: list[str] | None = None,
        include_rates: bool = True,
        max_queues: int = 10,
    ) -> dict[str, Any]:
        """按需获取 RabbitMQ 队列与吞吐诊断证据。

        适用于消息积压、消费速度不足、Unacked 增长、消费者异常等怀疑方向。
        queue_names 为空时，由 Provider 根据 task 自动定位相关队列。
        """
        request = RabbitMqDiagnosticsInput(
            task_id=task_id,
            queue_names=queue_names or [],
            include_rates=include_rates,
            max_queues=max_queues,
        )
        return _dump(await provider.get_rabbitmq_diagnostics(request))

    @tool("search_similar_experiments", args_schema=SimilarExperimentSearchInput)
    async def search_similar_experiments(
        query: str,
        middleware: str | None = None,
        top_k: int = 5,
        exclude_task_id: int | None = None,
    ) -> dict[str, Any]:
        """检索历史上与当前症状相似的实验。

        当需要用真实历史案例验证某个瓶颈假设时调用。query 应描述症状和关键指标，
        例如“Redis timeout、p95 上升 80%、CPU 正常、value 很大”，而不是只写“Redis 慢”。
        """
        request = SimilarExperimentSearchInput(
            query=query,
            middleware=middleware,
            top_k=top_k,
            exclude_task_id=exclude_task_id,
        )
        return _dump(await provider.search_similar_experiments(request))

    @tool("search_knowledge_base", args_schema=KnowledgeBaseSearchInput)
    async def search_knowledge_base(
        query: str,
        middleware: str | None = None,
        source_types: list[str] | None = None,
        top_k: int = 5,
    ) -> dict[str, Any]:
        """检索技术知识库，获取原理、官方依据或优化建议证据。

        适合在已经形成初步假设后查证，而不是用它替代实验数据本身。
        优先返回与当前中间件和问题直接相关的少量片段，控制上下文 Token 成本。
        """
        request = KnowledgeBaseSearchInput(
            query=query,
            middleware=middleware,
            source_types=source_types or [],
            top_k=top_k,
        )
        return _dump(await provider.search_knowledge_base(request))

    return [
        get_redis_diagnostics,
        get_mysql_diagnostics,
        get_rabbitmq_diagnostics,
        search_similar_experiments,
        search_knowledge_base,
    ]
