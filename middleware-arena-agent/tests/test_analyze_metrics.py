"""指标对比节点的变化率、缺失基线和严重等级测试。"""

import unittest

from app.graph.nodes.analyze_metrics import analyze_metrics


class AnalyzeMetricsNodeTest(unittest.TestCase):

    def test_compares_current_metrics_with_baseline(self) -> None:
        result = analyze_metrics({
            "metrics": {
                "qps": 1800,
                "p95Ms": 45,
                "errorRate": 0.005,
                "avgCpu": 0.82,
                "peakMemoryMb": 570,
            },
            "baseline_metrics": {
                "qps": 1200,
                "p95Ms": 80,
                "errorRate": 0.01,
                "avgCpu": 0.65,
                "peakMemoryMb": 512,
            },
        })

        findings = {item["metric"]: item for item in result["metric_findings"]}
        self.assertEqual(50.0, findings["qps"]["changePercent"])
        self.assertEqual("IMPROVED", findings["qps"]["assessment"])
        self.assertEqual(-43.75, findings["p95Ms"]["changePercent"])
        self.assertEqual("IMPROVED", findings["p95Ms"]["assessment"])
        self.assertEqual("warning", findings["avgCpu"]["level"])
        self.assertEqual(5, len(result["evidence"]))

    def test_keeps_current_values_when_baseline_is_missing(self) -> None:
        result = analyze_metrics({"metrics": {"qps": 900}})

        findings = {item["metric"]: item for item in result["metric_findings"]}
        self.assertEqual("NO_BASELINE", findings["qps"]["direction"])
        self.assertEqual("MISSING", findings["p95Ms"]["direction"])


if __name__ == "__main__":
    unittest.main()
"""指标对比节点的变化率、缺失基线和严重等级测试。"""
