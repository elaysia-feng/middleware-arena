import unittest
from unittest.mock import patch

from fastapi.testclient import TestClient

from app.main import app
from app.resource_advice import ResourceAdviceRequest, calculate_resource_advice


class ResourceAdviceTest(unittest.TestCase):

    def setUp(self) -> None:
        self.request = ResourceAdviceRequest.model_validate(
            {
                "experiment_type": "REDIS",
                "run_params": {"vus": 20},
                "rule_budget": {"cpus": 2.0, "memory_mb": 2048},
                "max_budget": {"cpus": 3.0, "memory_mb": 4096},
                "history": [
                    {"cpu_cores": 1.5, "memory_mb": 1800},
                    {"cpu_cores": 2.0, "memory_mb": 2200},
                ],
            }
        )

    @patch("app.resource_advice.request_resource_advice", side_effect=RuntimeError("offline"))
    def test_history_and_rule_still_work_when_llm_is_unavailable(self, _mock) -> None:
        response = calculate_resource_advice(self.request)

        self.assertFalse(response.llm_used)
        self.assertEqual(2.37, response.final_budget.cpus)
        self.assertEqual(2616, response.final_budget.memory_mb)

    @patch("app.resource_advice.request_resource_advice")
    def test_final_budget_uses_larger_llm_advice(self, mock_llm) -> None:
        mock_llm.return_value = {
            "cpus": 2.8,
            "memory_mb": 3000,
            "reason": "高并发需要更多资源",
        }

        response = calculate_resource_advice(self.request)

        self.assertTrue(response.llm_used)
        self.assertEqual(2.8, response.final_budget.cpus)
        self.assertEqual(3000, response.final_budget.memory_mb)

    @patch("app.resource_advice.request_resource_advice")
    def test_api_clamps_llm_advice_to_max_budget(self, mock_llm) -> None:
        mock_llm.return_value = {
            "cpus": 20.0,
            "memory_mb": 20000,
            "reason": "异常偏大的建议",
        }

        response = TestClient(app).post("/resource/advice", json=self.request.model_dump())

        self.assertEqual(200, response.status_code)
        self.assertEqual({"cpus": 3.0, "memory_mb": 4096}, response.json()["final_budget"])


if __name__ == "__main__":
    unittest.main()
