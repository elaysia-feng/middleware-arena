"""相似实验检索、相似度过滤和外部服务降级测试。"""

import unittest
from unittest.mock import AsyncMock, patch

from app.graph.nodes.retrieve_similar import retrieve_similar


class RetrieveSimilarNodeTest(unittest.IsolatedAsyncioTestCase):

    @patch(
        "app.graph.nodes.retrieve_similar.experiment_client.find_similar_experiments",
        new_callable=AsyncMock,
    )
    async def test_returns_validated_similar_experiments(self, find_similar: AsyncMock) -> None:
        find_similar.return_value = [{
            "taskId": 900,
            "versionId": 30,
            "middlewareType": "REDIS",
            "scenario": "HOT_KEY",
            "similarityScore": 0.91,
            "metrics": {"qps": 1700, "p95Ms": 50},
        }]

        result = await retrieve_similar({
            "task_id": 1001,
            "middleware_type": "REDIS",
            "metrics": {"qps": 1800, "p95Ms": 45},
        })

        find_similar.assert_awaited_once_with(task_id=1001, limit=5)
        self.assertEqual(900, result["similar_experiments"][0]["taskId"])
        self.assertEqual(0.91, result["similar_experiments"][0]["similarityScore"])

    @patch(
        "app.graph.nodes.retrieve_similar.experiment_client.find_similar_experiments",
        new_callable=AsyncMock,
        side_effect=RuntimeError("offline"),
    )
    async def test_degrades_when_search_is_unavailable(self, _find_similar: AsyncMock) -> None:
        result = await retrieve_similar({"task_id": 1001, "middleware_type": "REDIS"})

        self.assertEqual([], result["similar_experiments"])
        self.assertEqual("similar:unavailable", result["evidence"][0]["id"])


if __name__ == "__main__":
    unittest.main()
"""相似实验检索、相似度过滤和外部服务降级测试。"""
