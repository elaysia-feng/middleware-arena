"""单中间件、多中间件和通用专家路由测试。"""

from app.graph.router import route_middleware, select_middleware_route


def test_selects_redis_expert() -> None:
    result = select_middleware_route({"middleware_type": "redis"})

    assert result["middleware_type"] == "REDIS"
    assert result["middleware_route"] == "redis_expert"


def test_normalizes_rabbitmq_alias() -> None:
    result = select_middleware_route({"middleware_type": "rabbit-mq"})

    assert result["middleware_type"] == "RABBITMQ"
    assert result["middleware_route"] == "rabbitmq_expert"


def test_unknown_type_uses_generic_expert() -> None:
    result = select_middleware_route({"middleware_type": "kafka"})

    assert result["middleware_type"] == "KAFKA"
    assert result["middleware_route"] == "generic_expert"
    assert "通用证据分析" in result["middleware_route_reason"]


def test_conditional_route_reuses_node_decision() -> None:
    route = route_middleware(
        {
            "middleware_type": "redis",
            "middleware_route": "seata_expert",
        }
    )

    assert route == "seata_expert"


def test_conditional_route_can_fallback_to_direct_calculation() -> None:
    assert route_middleware({"middleware_type": "es"}) == "elasticsearch_expert"
"""单中间件、多中间件和通用专家路由测试。"""
