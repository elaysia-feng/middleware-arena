"""假设生成、证据裁决、置信度限制和规则降级测试。"""

import unittest

from app.graph.nodes.generate_hypothesis import generate_hypothesis
from app.graph.nodes.judge_bottleneck import judge_bottleneck
from app.graph.schemas import (
    BottleneckJudgeOutput,
    GeneratedHypothesis,
    HypothesisGenerationOutput,
)


class FakeHypothesisModel:
    def with_structured_output(self, schema, method):
        return self

    def invoke(self, prompt, config):
        return HypothesisGenerationOutput(
            summary="整理出 Redis N+1 候选假设",
            hypotheses=[
                GeneratedHypothesis(
                    type="REDIS_N_PLUS_ONE",
                    title="Redis N+1 调用",
                    summary="循环内 Redis 调用可能放大请求延迟",
                    confidence=0.82,
                    evidence_ids=["code:redis-loop", "invalid:evidence"],
                    verification=["统计单次请求 Redis 命令数量"],
                )
            ],
        )


class FakeJudgeModel:
    def with_structured_output(self, schema, method):
        return self

    def invoke(self, prompt, config):
        return BottleneckJudgeOutput(
            status="CONFIRMED",
            primary_hypothesis_id="hypothesis:redis_n_plus_one:1",
            confidence=0.86,
            reason="代码风险和延迟指标共同支持该判断",
            evidence_ids=["code:redis-loop", "metric:p95Ms"],
            suggestions=["改用批量读取或 pipeline"],
        )


class FakeSingleSourceJudgeModel:
    def with_structured_output(self, schema, method):
        return self

    def invoke(self, prompt, config):
        return BottleneckJudgeOutput(
            status="CONFIRMED",
            primary_hypothesis_id="hypothesis:redis_n_plus_one:1",
            confidence=0.90,
            reason="只有代码证据",
            evidence_ids=["code:redis-loop", "metric:unrelated"],
        )


class HypothesisAndJudgeTest(unittest.TestCase):

    def test_hypothesis_discards_unknown_evidence_ids(self) -> None:
        result = generate_hypothesis(
            {
                "task_id": 1001,
                "middleware_type": "REDIS",
                "hypotheses": [],
                "evidence": [{"id": "code:redis-loop", "source": "code_diff", "level": "warning"}],
            },
            chat_model=FakeHypothesisModel(),
        )

        hypothesis = result["ranked_hypotheses"][0]
        self.assertEqual(["code:redis-loop"], hypothesis["evidence_ids"])

    def test_judge_selects_only_existing_hypothesis_and_evidence(self) -> None:
        result = judge_bottleneck(
            {
                "task_id": 1001,
                "middleware_type": "REDIS",
                "ranked_hypotheses": [
                    {
                        "id": "hypothesis:redis_n_plus_one:1",
                        "type": "REDIS_N_PLUS_ONE",
                        "confidence": 0.82,
                        "evidence_ids": ["code:redis-loop", "metric:p95Ms"],
                    }
                ],
                "evidence": [
                    {"id": "code:redis-loop", "source": "code_diff", "level": "warning"},
                    {"id": "metric:p95Ms", "source": "metrics", "level": "critical"},
                ],
            },
            chat_model=FakeJudgeModel(),
        )

        self.assertEqual("CONFIRMED", result["bottleneck"]["status"])
        self.assertEqual("hypothesis:redis_n_plus_one:1", result["bottleneck"]["primary"]["id"])
        self.assertEqual(0.86, result["confidence"])

    def test_judge_rejects_unrelated_and_single_source_confirmation(self) -> None:
        result = judge_bottleneck(
            {
                "task_id": 1001,
                "middleware_type": "REDIS",
                "ranked_hypotheses": [
                    {
                        "id": "hypothesis:redis_n_plus_one:1",
                        "type": "REDIS_N_PLUS_ONE",
                        "confidence": 0.82,
                        "evidence_ids": ["code:redis-loop"],
                    }
                ],
                "evidence": [
                    {"id": "code:redis-loop", "source": "code_diff", "level": "warning"},
                    {"id": "metric:unrelated", "source": "metrics", "level": "critical"},
                ],
            },
            chat_model=FakeSingleSourceJudgeModel(),
        )

        self.assertEqual("UNCERTAIN", result["bottleneck"]["status"])
        self.assertEqual(["code:redis-loop"], result["bottleneck"]["evidenceIds"])
        self.assertLessEqual(result["confidence"], 0.69)


if __name__ == "__main__":
    unittest.main()
"""假设生成、证据裁决、置信度限制和规则降级测试。"""
