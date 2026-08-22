"""中间件实验分析主图构建入口。"""

from functools import partial
from typing import Any

from langgraph.graph import END, START, StateGraph

from app.graph.nodes.analyze_code import analyze_code
from app.graph.nodes.analyze_logs import analyze_logs
from app.graph.nodes.analyze_metrics import analyze_metrics
from app.graph.nodes.generate_hypothesis import generate_hypothesis
from app.graph.nodes.generate_patch import generate_patch
from app.graph.nodes.generate_report import generate_report
from app.graph.nodes.judge_bottleneck import judge_bottleneck
from app.graph.nodes.load_context import load_context
from app.graph.nodes.merge_evidence import merge_evidence
from app.graph.nodes.retrieve_similar import retrieve_similar
from app.graph.router import (
    route_by_confidence,
    route_middleware,
    select_confidence_route,
    select_middleware_route,
)
from app.graph.state import AnalysisState
from app.graph.subgraphs.elasticsearch.graph import build_elasticsearch_subgraph
from app.graph.subgraphs.generic.graph import build_generic_subgraph
from app.graph.subgraphs.rabbitmq.graph import build_rabbitmq_subgraph
from app.graph.subgraphs.redis.graph import build_redis_subgraph
from app.graph.subgraphs.seata.graph import build_seata_subgraph
from app.tools.providers import AnalysisToolProvider


def build_analysis_graph(
    provider: AnalysisToolProvider | None = None,
    chat_model: Any | None = None,
):
    """构建从上下文加载到候选 Patch 和最终报告的完整诊断图。"""
    graph = StateGraph(AnalysisState)

    graph.add_node("load_context", load_context)
    graph.add_node("analyze_metrics", analyze_metrics)
    graph.add_node("analyze_logs", analyze_logs)
    graph.add_node("analyze_code", analyze_code)
    graph.add_node("retrieve_similar", retrieve_similar)
    graph.add_node("merge_evidence", merge_evidence)
    graph.add_node("middleware_router", select_middleware_route)

    graph.add_node("redis_expert", build_redis_subgraph(provider, chat_model))
    graph.add_node("rabbitmq_expert", build_rabbitmq_subgraph(provider, chat_model))
    graph.add_node("seata_expert", build_seata_subgraph(provider, chat_model))
    graph.add_node("elasticsearch_expert", build_elasticsearch_subgraph(chat_model))
    graph.add_node("generic_expert", build_generic_subgraph(chat_model))

    graph.add_node("generate_hypothesis", partial(generate_hypothesis, chat_model=chat_model))
    graph.add_node("judge_bottleneck", partial(judge_bottleneck, chat_model=chat_model))
    graph.add_node("confidence_router", select_confidence_route)
    graph.add_node("generate_patch", partial(generate_patch, chat_model=chat_model))
    graph.add_node("generate_report", partial(generate_report, chat_model=chat_model))

    graph.add_edge(START, "load_context")
    for evidence_node in ("analyze_metrics", "analyze_logs", "analyze_code", "retrieve_similar"):
        graph.add_edge("load_context", evidence_node)
    graph.add_edge(
        ["analyze_metrics", "analyze_logs", "analyze_code", "retrieve_similar"],
        "merge_evidence",
    )
    graph.add_edge("merge_evidence", "middleware_router")
    graph.add_conditional_edges(
        "middleware_router",
        route_middleware,
        {
            "redis_expert": "redis_expert",
            "rabbitmq_expert": "rabbitmq_expert",
            "seata_expert": "seata_expert",
            "elasticsearch_expert": "elasticsearch_expert",
            "generic_expert": "generic_expert",
        },
    )
    for expert_node in (
        "redis_expert",
        "rabbitmq_expert",
        "seata_expert",
        "elasticsearch_expert",
        "generic_expert",
    ):
        graph.add_edge(expert_node, "generate_hypothesis")
    graph.add_edge("generate_hypothesis", "judge_bottleneck")
    graph.add_edge("judge_bottleneck", "confidence_router")
    graph.add_conditional_edges(
        "confidence_router",
        route_by_confidence,
        {
            "generate_patch": "generate_patch",
            "generate_report": "generate_report",
        },
    )
    graph.add_edge("generate_patch", "generate_report")
    graph.add_edge("generate_report", END)
    return graph.compile()


analysis_graph = build_analysis_graph()
