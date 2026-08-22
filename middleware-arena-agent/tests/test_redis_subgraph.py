"""Redis 专家子图的信号、诊断工具和候选假设测试。"""

import unittest

from app.graph.subgraphs.redis.nodes import (
    collect_redis_diagnostics,
    collect_redis_signals,
    diagnose_redis,
    route_redis_diagnostics,
)
from app.graph.subgraphs.redis.schemas import (
    RedisDiagnosisOutput,
    RedisHypothesisOutput,
)
from app.tools.tool_schemas import ToolResult


class FakeRedisProvider:
    async def get_redis_diagnostics(self, request):
        return ToolResult(
            status="ok",
            summary=f"task={request.task_id} Redis 诊断完成",
            data={"slowlogCount": 2},
            evidence=["SlowLog 中发现两条耗时超过 10ms 的 GET 命令"],
        )


class FakeStructuredModel:
    def with_structured_output(self, schema, method):
        return self

    def invoke(self, prompt):
        return RedisDiagnosisOutput(
            summary="Redis 请求往返次数可能增加",
            hypotheses=[
                RedisHypothesisOutput(
                    type="REDIS_N_PLUS_ONE",
                    title="Redis N+1 调用",
                    summary="循环内 Redis 调用可能造成延迟上升",
                    confidence=0.82,
                    evidence_ids=["code:1:loop_remote_call", "不存在的证据"],
                    verification=["检查命令调用次数"],
                    suggestions=["使用批量读取或 pipeline"],
                )
            ],
        )


class RedisSubgraphTest(unittest.IsolatedAsyncioTestCase):

    def test_collects_redis_signals_and_requests_tool(self) -> None:
        result = collect_redis_signals({
            "evidence": [
                {
                    "id": "log:redis_error",
                    "source": "logs",
                    "message": "Redis command timed out",
                    "data": {"category": "REDIS_ERROR"},
                },
                {
                    "id": "log:rabbitmq",
                    "source": "logs",
                    "message": "RabbitMQ channel shutdown",
                    "data": {"category": "RABBITMQ_DELIVERY"},
                },
            ]
        })

        self.assertEqual(["log:redis_error"], [item["id"] for item in result["redis_signals"]])
        self.assertEqual("collect_redis_diagnostics", route_redis_diagnostics(result))

    async def test_collects_bounded_redis_tool_result(self) -> None:
        result = await collect_redis_diagnostics(
            {"task_id": 1001},
            provider=FakeRedisProvider(),
        )

        self.assertEqual("ok", result["redis_diagnostics"]["status"])
        self.assertEqual(2, result["redis_diagnostics"]["data"]["slowlogCount"])

    def test_llm_hypothesis_must_reference_existing_evidence(self) -> None:
        result = diagnose_redis(
            {
                "task_id": 1001,
                "evidence": [
                    {
                        "id": "code:1:loop_remote_call",
                        "source": "code_diff",
                        "message": "循环内调用 Redis",
                        "data": {"category": "LOOP_REMOTE_CALL"},
                    }
                ],
                "redis_diagnostics": {"status": "skipped", "warnings": []},
            },
            chat_model=FakeStructuredModel(),
        )

        hypothesis = result["hypotheses"][0]
        self.assertEqual(["code:1:loop_remote_call"], hypothesis["evidence_ids"])
        self.assertEqual("REDIS", hypothesis["expert"])


if __name__ == "__main__":
    unittest.main()
"""Redis 专家子图的信号、诊断工具和候选假设测试。"""
