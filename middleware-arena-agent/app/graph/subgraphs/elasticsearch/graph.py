"""Elasticsearch 专家 SubGraph 构建入口。"""

from functools import partial
from typing import Any

from langgraph.graph import END, START, StateGraph

from app.graph.state import AnalysisState
from app.graph.subgraphs.elasticsearch.nodes import collect_elasticsearch_signals, diagnose_elasticsearch


def build_elasticsearch_subgraph(chat_model: Any | None = None):
    """构建“收集 ES 信号 → 专家诊断”的独立子图。"""
    graph = StateGraph(AnalysisState)
    graph.add_node("collect_elasticsearch_signals", collect_elasticsearch_signals)
    graph.add_node("diagnose_elasticsearch", partial(diagnose_elasticsearch, chat_model=chat_model))
    graph.add_edge(START, "collect_elasticsearch_signals")
    graph.add_edge("collect_elasticsearch_signals", "diagnose_elasticsearch")
    graph.add_edge("diagnose_elasticsearch", END)
    return graph.compile()


elasticsearch_subgraph = build_elasticsearch_subgraph()
