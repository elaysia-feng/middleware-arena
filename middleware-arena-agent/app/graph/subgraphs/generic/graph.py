"""通用中间件专家 SubGraph 构建入口。"""

from functools import partial
from typing import Any

from langgraph.graph import END, START, StateGraph

from app.graph.state import AnalysisState
from app.graph.subgraphs.generic.nodes import diagnose_generic


def build_generic_subgraph(chat_model: Any | None = None):
    """构建未知或未识别中间件使用的通用诊断子图。"""
    graph = StateGraph(AnalysisState)
    graph.add_node("diagnose_generic", partial(diagnose_generic, chat_model=chat_model))
    graph.add_edge(START, "diagnose_generic")
    graph.add_edge("diagnose_generic", END)
    return graph.compile()


generic_subgraph = build_generic_subgraph()
