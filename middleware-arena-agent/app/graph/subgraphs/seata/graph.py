"""Seata 专家 SubGraph 构建入口。"""

from functools import partial
from typing import Any

from langgraph.graph import END, START, StateGraph

from app.graph.state import AnalysisState
from app.graph.subgraphs.seata.nodes import (
    collect_seata_diagnostics,
    collect_seata_signals,
    diagnose_seata,
    route_seata_diagnostics,
)
from app.tools.providers import AnalysisToolProvider


def build_seata_subgraph(
    provider: AnalysisToolProvider | None = None,
    chat_model: Any | None = None,
):
    """构建 Seata 信号筛选、按需数据库诊断和专家判断子图。"""
    graph = StateGraph(AnalysisState)
    graph.add_node("collect_seata_signals", collect_seata_signals)
    graph.add_node(
        "collect_seata_diagnostics",
        partial(collect_seata_diagnostics, provider=provider),
    )
    graph.add_node("diagnose_seata", partial(diagnose_seata, chat_model=chat_model))
    graph.add_edge(START, "collect_seata_signals")
    graph.add_conditional_edges(
        "collect_seata_signals",
        route_seata_diagnostics,
        {
            "collect_seata_diagnostics": "collect_seata_diagnostics",
            "diagnose_seata": "diagnose_seata",
        },
    )
    graph.add_edge("collect_seata_diagnostics", "diagnose_seata")
    graph.add_edge("diagnose_seata", END)
    return graph.compile()


seata_subgraph = build_seata_subgraph()
