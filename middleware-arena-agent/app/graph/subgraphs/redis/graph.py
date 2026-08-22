"""Redis 专家 SubGraph 构建入口。"""

from functools import partial
from typing import Any

from langgraph.graph import END, START, StateGraph

from app.graph.state import AnalysisState
from app.graph.subgraphs.redis.nodes import (
    collect_redis_diagnostics,
    collect_redis_signals,
    diagnose_redis,
    route_redis_diagnostics,
)
from app.tools.providers import AnalysisToolProvider


def build_redis_subgraph(
    provider: AnalysisToolProvider | None = None,
    chat_model: Any | None = None,
):
    """构建可嵌入主图的 Redis 专家 SubGraph。"""
    graph = StateGraph(AnalysisState)
    graph.add_node("collect_redis_signals", collect_redis_signals)
    graph.add_node(
        "collect_redis_diagnostics",
        partial(collect_redis_diagnostics, provider=provider),
    )
    graph.add_node("diagnose_redis", partial(diagnose_redis, chat_model=chat_model))

    graph.add_edge(START, "collect_redis_signals")
    graph.add_conditional_edges(
        "collect_redis_signals",
        route_redis_diagnostics,
        {
            "collect_redis_diagnostics": "collect_redis_diagnostics",
            "diagnose_redis": "diagnose_redis",
        },
    )
    graph.add_edge("collect_redis_diagnostics", "diagnose_redis")
    graph.add_edge("diagnose_redis", END)
    return graph.compile()


redis_subgraph = build_redis_subgraph()
