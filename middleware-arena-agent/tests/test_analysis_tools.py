import unittest

from app.tools import ANALYSIS_TOOL_NAMES, ToolResult, build_analysis_tools
from app.tools.tool_schemas import (
    KnowledgeBaseSearchInput,
    MySqlDiagnosticsInput,
    RabbitMqDiagnosticsInput,
    RedisDiagnosticsInput,
    SimilarExperimentSearchInput,
)


class FakeAnalysisToolProvider:
    """只验证 Tool Schema / 路由，不依赖真实 Redis、MySQL、RabbitMQ。"""

    async def get_redis_diagnostics(self, request: RedisDiagnosticsInput) -> ToolResult:
        return ToolResult(summary=f"redis task={request.task_id}", data={"task_id": request.task_id})

    async def get_mysql_diagnostics(self, request: MySqlDiagnosticsInput) -> ToolResult:
        return ToolResult(summary=f"mysql task={request.task_id}", data={"task_id": request.task_id})

    async def get_rabbitmq_diagnostics(self, request: RabbitMqDiagnosticsInput) -> ToolResult:
        return ToolResult(summary=f"rabbitmq task={request.task_id}", data={"queues": request.queue_names})

    async def search_similar_experiments(self, request: SimilarExperimentSearchInput) -> ToolResult:
        return ToolResult(summary="similar experiments", data={"query": request.query, "top_k": request.top_k})

    async def search_knowledge_base(self, request: KnowledgeBaseSearchInput) -> ToolResult:
        return ToolResult(summary="knowledge base", data={"query": request.query, "top_k": request.top_k})


class AnalysisToolsTest(unittest.IsolatedAsyncioTestCase):

    def setUp(self) -> None:
        self.tools = build_analysis_tools(FakeAnalysisToolProvider())
        self.tools_by_name = {tool.name: tool for tool in self.tools}

    def test_exposes_only_expected_on_demand_tools(self) -> None:
        self.assertEqual(list(ANALYSIS_TOOL_NAMES), [tool.name for tool in self.tools])
        self.assertNotIn("get_experiment_result", self.tools_by_name)
        self.assertNotIn("get_baseline_result", self.tools_by_name)

    def test_mysql_tool_does_not_expose_arbitrary_sql(self) -> None:
        schema = self.tools_by_name["get_mysql_diagnostics"].args_schema.model_json_schema()
        self.assertNotIn("sql", schema["properties"])
        self.assertIn("task_id", schema["properties"])

    async def test_redis_tool_routes_validated_request_to_provider(self) -> None:
        result = await self.tools_by_name["get_redis_diagnostics"].ainvoke(
            {
                "task_id": 1001,
                "include_big_keys": True,
                "include_slowlog": False,
                "max_big_keys": 5,
                "max_slowlog_entries": 10,
            }
        )

        self.assertEqual("ok", result["status"])
        self.assertEqual(1001, result["data"]["task_id"])

    async def test_similar_experiment_search_has_bounded_top_k(self) -> None:
        result = await self.tools_by_name["search_similar_experiments"].ainvoke(
            {
                "query": "Redis timeout and p95 latency spike",
                "middleware": "redis",
                "top_k": 5,
                "exclude_task_id": 1001,
            }
        )

        self.assertEqual(5, result["data"]["top_k"])


if __name__ == "__main__":
    unittest.main()
