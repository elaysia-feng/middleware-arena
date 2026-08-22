"""代码 Diff 分析节点的规则匹配、证据引用和空输入测试。"""

import unittest

from app.graph.nodes.analyze_code import analyze_code


class AnalyzeCodeNodeTest(unittest.TestCase):

    def test_extracts_transaction_and_loop_remote_call_risks(self) -> None:
        result = analyze_code({
            "code_diff": [{
                "path": "src/main/java/OrderService.java",
                "changeType": "MODIFIED",
                "diffLines": [
                    {"type": "ADD", "newLineNo": 20, "content": "@Transactional"},
                    {"type": "ADD", "newLineNo": 21, "content": "for (Order order : orders) {"},
                    {"type": "ADD", "newLineNo": 22, "content": "productClient.getProduct(order.getProductId());"},
                ],
            }]
        })

        categories = {item["category"] for item in result["code_findings"]}
        self.assertIn("TRANSACTION_BOUNDARY", categories)
        self.assertIn("LOOP_REMOTE_CALL", categories)
        self.assertIn("TRANSACTION_REMOTE_CALL", categories)

    def test_marks_removed_risk_as_removed_signal(self) -> None:
        result = analyze_code({
            "code_diff": [{
                "path": "src/main/java/CacheService.java",
                "changeType": "MODIFIED",
                "diffLines": [{
                    "type": "REMOVE",
                    "oldLineNo": 10,
                    "content": "redisTemplate.keys(\"*\");",
                }],
            }]
        })

        finding = result["code_findings"][0]
        self.assertEqual("REDIS_FULL_SCAN", finding["category"])
        self.assertEqual("REMOVED_SIGNAL", finding["assessment"])
        self.assertEqual("info", finding["level"])


if __name__ == "__main__":
    unittest.main()
"""代码 Diff 分析节点的规则匹配、证据引用和空输入测试。"""
