"""Langfuse Prompt 名称常量占位。

1. 集中维护所有 Prompt 名称，避免散落字符串。
2. 让 LangGraph Node 与 Langfuse Prompt Management 解耦。
3. 后续可按环境映射 dev/staging/prod label。

TODO:
- [ ] 补齐所有 Prompt 名称。
- [ ] 与 Langfuse 中实际 Prompt 名保持一致。
"""

METRICS_ANALYSIS = "middleware-metrics-analysis"
CODE_ANALYSIS = "middleware-code-analysis"
HYPOTHESIS_GENERATOR = "middleware-hypothesis-generator"
BOTTLENECK_JUDGE = "middleware-bottleneck-judge"
PATCH_GENERATOR = "middleware-patch-generator"
REPORT_GENERATOR = "middleware-report-generator"
REDIS_DIAGNOSIS = "middleware-redis-diagnosis"
RABBITMQ_DIAGNOSIS = "middleware-rabbitmq-diagnosis"
SEATA_DIAGNOSIS = "middleware-seata-diagnosis"
ELASTICSEARCH_DIAGNOSIS = "middleware-elasticsearch-diagnosis"
