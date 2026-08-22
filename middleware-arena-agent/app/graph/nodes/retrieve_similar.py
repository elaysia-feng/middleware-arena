"""历史相似实验检索节点。

当前由 experiment-service 从历史成功任务中筛选同中间件实验，并按场景和核心指标距离排序。
检索失败属于可降级故障，不应阻断本次 Agent 分析。
"""

import logging
from typing import Any

from app.clients.experiment import experiment_client
from app.graph.state import AnalysisState
from app.schemas.analysis import SimilarExperiment

logger = logging.getLogger(__name__)


async def retrieve_similar(state: AnalysisState) -> dict[str, list[dict[str, Any]]]:
    """检索最多五条相似实验，并返回标准化结果和检索证据。"""
    task_id = state["task_id"]
    query = _build_search_query(state)
    try:
        raw_matches = await experiment_client.find_similar_experiments(task_id=task_id, limit=5)
        matches = [
            SimilarExperiment.model_validate(item).model_dump(mode="json", by_alias=True)
            for item in raw_matches
        ]
    except Exception as exc:
        logger.warning("相似实验检索失败，降级为空列表: taskId=%s cause=%s", task_id, exc)
        return {
            "similar_experiments": [],
            "evidence": [{
                "id": "similar:unavailable",
                "source": "similar_experiments",
                "level": "warning",
                "message": "历史相似实验检索不可用，本次诊断仅使用当前实验证据",
                "data": {"query": query, "resultCount": 0},
            }],
        }

    if not matches:
        return {
            "similar_experiments": [],
            "evidence": [{
                "id": "similar:not-found",
                "source": "similar_experiments",
                "level": "info",
                "message": "没有找到可用于佐证的历史相似实验",
                "data": {"query": query, "resultCount": 0},
            }],
        }

    return {
        "similar_experiments": matches,
        "evidence": [{
            "id": "similar:matches",
            "source": "similar_experiments",
            "level": "info",
            "message": f"找到 {len(matches)} 条历史相似实验",
            "data": {
                "query": query,
                "resultCount": len(matches),
                "matches": [
                    {
                        "taskId": item["taskId"],
                        "similarityScore": item["similarityScore"],
                        "scenario": item.get("scenario"),
                    }
                    for item in matches
                ],
            },
        }],
    }


def _build_search_query(state: AnalysisState) -> str:
    middleware_type = str(state.get("middleware_type") or "UNKNOWN").upper()
    metrics = state.get("metrics") or {}
    config = state.get("config") or {}
    parts = [f"middleware={middleware_type}"]
    for key in ("qps", "p95Ms", "errorRate", "avgCpu", "peakMemoryMb"):
        value = metrics.get(key)
        if isinstance(value, (int, float)) and not isinstance(value, bool):
            parts.append(f"{key}={value}")
    for key in ("vus", "concurrency", "duration"):
        value = config.get(key)
        if value is not None:
            parts.append(f"{key}={str(value)[:50]}")
    return "; ".join(parts)[:500]
