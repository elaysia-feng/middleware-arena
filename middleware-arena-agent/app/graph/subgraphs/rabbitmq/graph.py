"""RabbitMQ 专家 SubGraph 构建入口。"""

from functools import partial
from typing import Any

from langgraph.graph import END, START, StateGraph

from app.graph.state import AnalysisState
from app.graph.subgraphs.rabbitmq.nodes import (
    collect_rabbitmq_diagnostics,
    collect_rabbitmq_signals,
    diagnose_rabbitmq,
    route_rabbitmq_diagnostics,
)
from app.tools.providers import AnalysisToolProvider


def build_rabbitmq_subgraph(
    provider: AnalysisToolProvider | None = None,
    chat_model: Any | None = None,
):
    """构建 RabbitMQ 信号筛选、按需实例诊断和专家判断子图。"""
    graph = StateGraph(AnalysisState)
    graph.add_node("collect_rabbitmq_signals", collect_rabbitmq_signals)
    graph.add_node(
        "collect_rabbitmq_diagnostics",
        partial(collect_rabbitmq_diagnostics, provider=provider),
    )
    graph.add_node("diagnose_rabbitmq", partial(diagnose_rabbitmq, chat_model=chat_model))
    graph.add_edge(START, "collect_rabbitmq_signals")
    graph.add_conditional_edges(
        "collect_rabbitmq_signals",
        route_rabbitmq_diagnostics,
        {
            "collect_rabbitmq_diagnostics": "collect_rabbitmq_diagnostics",
            "diagnose_rabbitmq": "diagnose_rabbitmq",
        },
    )
    graph.add_edge("collect_rabbitmq_diagnostics", "diagnose_rabbitmq")
    graph.add_edge("diagnose_rabbitmq", END)
    return graph.compile()


rabbitmq_subgraph = build_rabbitmq_subgraph()
