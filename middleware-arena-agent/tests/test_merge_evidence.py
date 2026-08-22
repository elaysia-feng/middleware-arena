"""并行节点证据合并、去重和摘要统计测试。"""

import unittest

from app.graph.nodes.merge_evidence import merge_evidence
from app.graph.state import merge_evidence_reducer


class MergeEvidenceNodeTest(unittest.TestCase):

    def test_reducer_keeps_parallel_evidence_and_deduplicates_ids(self) -> None:
        merged = merge_evidence_reducer(
            [{"id": "metric:qps", "message": "old"}],
            [
                {"id": "log:timeout", "message": "timeout"},
                {"id": "metric:qps", "message": "new"},
            ],
        )

        evidence_by_id = {item["id"]: item for item in merged}
        self.assertEqual(2, len(merged))
        self.assertEqual("new", evidence_by_id["metric:qps"]["message"])

    def test_sorts_evidence_and_reports_data_limitations(self) -> None:
        result = merge_evidence({
            "evidence": [
                {
                    "id": "metric:qps",
                    "source": "metrics",
                    "level": "info",
                    "message": "QPS 正常",
                    "data": {"current": 1000},
                },
                {
                    "id": "log:unavailable",
                    "source": "logs",
                    "level": "warning",
                    "message": "日志不可用",
                    "data": {},
                },
                {
                    "id": "code:1:redis_full_scan",
                    "source": "code_diff",
                    "level": "critical",
                    "message": "发现 KEYS",
                    "data": {},
                },
                {
                    "id": "similar:not-found",
                    "source": "similar_experiments",
                    "level": "info",
                    "message": "无相似实验",
                    "data": {},
                },
            ]
        })

        self.assertEqual("critical", result["merged_evidence"][0]["level"])
        self.assertEqual(1, result["evidence_summary"]["critical"])
        self.assertIn("实验日志不可用", result["evidence_summary"]["limitations"])
        self.assertLess(result["evidence_summary"]["coverageScore"], 1.0)


if __name__ == "__main__":
    unittest.main()
"""并行节点证据合并、去重和摘要统计测试。"""
