"""LangGraph 条件路由。

路由节点只负责识别中间件类型并选择专家分支，不在这里执行具体诊断。
未知类型进入通用专家分支，避免因为新增类型或脏数据直接中断工作流。

瓶颈裁决后再根据证据状态决定是否生成候选 Patch。只有已确认且达到阈值的结论
才能进入 Patch 节点，其余情况直接生成报告并提示补充证据。
"""

from typing import Any


MIDDLEWARE_ROUTES = {
    "REDIS": "redis_expert",
    "RABBITMQ": "rabbitmq_expert",
    "SEATA": "seata_expert",
    "ELASTICSEARCH": "elasticsearch_expert",
}

MIDDLEWARE_ALIASES = {
    "RABBIT_MQ": "RABBITMQ",
    "MQ": "RABBITMQ",
    "ES": "ELASTICSEARCH",
}

ALLOWED_MIDDLEWARE_ROUTES = {*MIDDLEWARE_ROUTES.values(), "generic_expert"}
CONFIDENCE_ROUTES = {"generate_patch", "generate_report"}
DEFAULT_PATCH_CONFIDENCE = 0.75


def select_middleware_route(state: dict[str, Any]) -> dict[str, str]:
    """规范化中间件类型，并把专家分支选择写入 State。"""
    middleware_type = _normalize_middleware_type(state.get("middleware_type"))
    middleware_route = MIDDLEWARE_ROUTES.get(middleware_type, "generic_expert")

    if middleware_route == "generic_expert":
        reason = f"未找到 {middleware_type} 专家分支，使用通用证据分析"
    else:
        reason = f"识别为 {middleware_type}，进入 {middleware_route} 分支"

    return {
        "middleware_type": middleware_type,
        "middleware_route": middleware_route,
        "middleware_route_reason": reason,
    }


def route_middleware(state: dict[str, Any]) -> str:
    """供 ``add_conditional_edges`` 使用，返回下一跳节点名称。"""
    selected_route = state.get("middleware_route")
    if selected_route in ALLOWED_MIDDLEWARE_ROUTES:
        return str(selected_route)
    return select_middleware_route(state)["middleware_route"]


def select_confidence_route(state: dict[str, Any]) -> dict[str, Any]:
    """把裁决结果转换为显式路由状态，便于报告和 Langfuse 追踪。"""
    judgement = state.get("judgement") or {}
    confidence = float(state.get("confidence") or 0)
    threshold = float(
        state.get("patch_confidence_threshold") or DEFAULT_PATCH_CONFIDENCE
    )
    has_primary = bool((state.get("bottleneck") or {}).get("primary"))

    if (
        judgement.get("status") == "CONFIRMED"
        and has_primary
        and confidence >= threshold
    ):
        route = "generate_patch"
        reason = f"瓶颈已确认且置信度 {confidence:.2f} 达到阈值 {threshold:.2f}"
    else:
        route = "generate_report"
        reason = (
            f"裁决状态为 {judgement.get('status', 'UNKNOWN')}，"
            f"置信度 {confidence:.2f}，暂不生成代码 Patch"
        )

    return {
        "confidence_route": route,
        "confidence_route_reason": reason,
        "patch_confidence_threshold": threshold,
        "next_action": (
            "GENERATE_CANDIDATE_PATCH"
            if route == "generate_patch"
            else "COLLECT_MORE_EVIDENCE"
        ),
    }


def route_by_confidence(state: dict[str, Any]) -> str:
    """返回 Judge 后的下一跳节点名称。"""
    selected_route = state.get("confidence_route")
    if selected_route in CONFIDENCE_ROUTES:
        return str(selected_route)
    return select_confidence_route(state)["confidence_route"]


def _normalize_middleware_type(value: Any) -> str:
    """把常见别名归一化为工作流内部使用的类型名称。"""
    normalized = str(value or "").strip().upper()
    normalized = normalized.replace("-", "_").replace(" ", "_")
    normalized = MIDDLEWARE_ALIASES.get(normalized, normalized)
    return normalized or "UNKNOWN"
