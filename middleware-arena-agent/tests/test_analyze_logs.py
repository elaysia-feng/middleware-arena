"""日志分析节点的规则匹配、聚合、截断和空日志测试。"""

import unittest

from app.graph.nodes.analyze_logs import analyze_logs


class AnalyzeLogsNodeTest(unittest.TestCase):

    def test_groups_known_errors_and_redacts_sensitive_values(self) -> None:
        result = analyze_logs({
            "logs": [
                "ERROR HikariPool-1 - Connection is not available password=secret123",
                "WARN timeout waiting for connection",
                "java.lang.OutOfMemoryError: Java heap space",
                "java.lang.OutOfMemoryError: Java heap space",
            ]
        })

        findings = {item["category"]: item for item in result["log_findings"]}
        self.assertEqual(2, findings["OUT_OF_MEMORY"]["count"])
        self.assertEqual("critical", findings["OUT_OF_MEMORY"]["level"])
        self.assertIn("password=[REDACTED]", findings["CONNECTION_POOL_EXHAUSTED"]["samples"][0])

    def test_reports_missing_logs_without_failing_the_workflow(self) -> None:
        result = analyze_logs({"logs": []})

        self.assertEqual([], result["log_findings"])
        self.assertEqual("log:unavailable", result["evidence"][0]["id"])


if __name__ == "__main__":
    unittest.main()
"""日志分析节点的规则匹配、聚合、截断和空日志测试。"""
