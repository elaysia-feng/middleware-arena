"""实验上下文加载、可信字段覆盖和异常输入测试。"""

import unittest
from unittest.mock import AsyncMock, patch

from app.graph.nodes.load_context import load_context


class LoadContextNodeTest(unittest.IsolatedAsyncioTestCase):

    @patch(
        "app.graph.nodes.load_context.experiment_client.get_analysis_context",
        new_callable=AsyncMock,
    )
    async def test_loads_and_normalizes_experiment_context(self, get_context: AsyncMock) -> None:
        get_context.return_value = {
            "taskId": 1001,
            "userId": 7,
            "versionId": 31,
            "baselineTaskId": 900,
            "middlewareType": "redis",
            "config": {"vus": 100},
            "files": [{"path": "src/App.java", "content": "class App {}"}],
            "codeDiff": [{"path": "src/App.java", "changeType": "MODIFIED"}],
            "metrics": {"qps": 1800, "p95Ms": 45},
            "baselineMetrics": {"qps": 1200, "p95Ms": 80},
            "logs": ["WARN redis connection pool waiting"],
        }

        result = await load_context({"task_id": 1001, "baseline_task_id": 900})

        get_context.assert_awaited_once_with(task_id=1001, baseline_task_id=900)
        self.assertEqual("REDIS", result["middleware_type"])
        self.assertEqual(31, result["version_id"])
        self.assertEqual(1800, result["metrics"]["qps"])
        self.assertEqual(1200, result["baseline_metrics"]["qps"])
        self.assertEqual(["WARN redis connection pool waiting"], result["logs"])

    @patch(
        "app.graph.nodes.load_context.experiment_client.get_analysis_context",
        new_callable=AsyncMock,
    )
    async def test_rejects_mismatched_task_context(self, get_context: AsyncMock) -> None:
        get_context.return_value = {
            "taskId": 2002,
            "userId": 7,
            "versionId": 31,
            "middlewareType": "redis",
        }

        with self.assertRaisesRegex(ValueError, "taskId"):
            await load_context({"task_id": 1001})


if __name__ == "__main__":
    unittest.main()
"""实验上下文加载、可信字段覆盖和异常输入测试。"""
