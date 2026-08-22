"""各中间件专家子图信号筛选和按需工具路由测试。"""

import unittest

from app.graph.subgraphs.elasticsearch.nodes import collect_elasticsearch_signals
from app.graph.subgraphs.rabbitmq.nodes import collect_rabbitmq_signals
from app.graph.subgraphs.seata.nodes import collect_seata_signals


class ExpertSignalNodesTest(unittest.TestCase):

    def test_rabbitmq_signal_does_not_match_redis_evidence(self) -> None:
        result = collect_rabbitmq_signals({
            "evidence": [{"id": "log:redis", "message": "Redis command timed out", "data": {"category": "REDIS_ERROR"}}]
        })
        self.assertEqual([], result["rabbitmq_signals"])

    def test_seata_signal_matches_global_transaction(self) -> None:
        result = collect_seata_signals({
            "evidence": [{"id": "log:seata", "message": "global transaction rollback", "data": {"category": "SEATA_TRANSACTION"}}]
        })
        self.assertEqual(["log:seata"], [item["id"] for item in result["seata_signals"]])

    def test_elasticsearch_signal_matches_deep_paging(self) -> None:
        result = collect_elasticsearch_signals({
            "evidence": [{"id": "code:es", "message": "Elasticsearch 深分页", "data": {"category": "ELASTICSEARCH_DEEP_PAGING"}}]
        })
        self.assertEqual(["code:es"], [item["id"] for item in result["elasticsearch_signals"]])


if __name__ == "__main__":
    unittest.main()
"""各中间件专家子图信号筛选和按需工具路由测试。"""
