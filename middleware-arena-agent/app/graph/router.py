"""LangGraph 条件路由占位。

1. 根据 middleware_type 选择 Redis / RabbitMQ / Seata / Elasticsearch SubGraph。
2. 根据分析结果决定是否进入 Patch 生成、直接报告或再次验证。
3. 对非法/未知中间件提供统一 fallback 路径。

TODO:
- [ ] 实现 route_middleware(state)。
- [ ] 实现 route_after_diagnosis(state)。
- [ ] 实现最大迭代次数保护。
"""
