"""日志分析节点。

本节点先用确定性规则完成日志清洗、脱敏、分类和聚合，不让重复日志直接占满后续 LLM 上下文。
它只生成日志发现和证据，不在这里直接判断最终瓶颈。
"""

import re
from typing import Any

from app.graph.state import AnalysisState


LOG_RULES = (
    {
        "category": "OUT_OF_MEMORY",
        "level": "critical",
        "keywords": ("outofmemoryerror", "java heap space", "gc overhead limit exceeded", "cannot allocate memory"),
        "title": "检测到内存溢出",
    },
    {
        "category": "DATABASE_LOCK",
        "level": "critical",
        "keywords": ("deadlock", "lock wait timeout", "could not obtain lock"),
        "title": "检测到数据库锁等待或死锁",
    },
    {
        "category": "MAPPING_ERROR",
        "level": "critical",
        "keywords": ("unknown column", "invalid bound statement", "mappingexception", "mapping error", "result map"),
        "title": "检测到字段或持久化映射错误",
    },
    {
        "category": "ELASTICSEARCH_REJECTED",
        "level": "critical",
        "keywords": ("circuit_breaking_exception", "rejected_execution_exception", "all shards failed"),
        "title": "检测到 Elasticsearch 拒绝或分片异常",
    },
    {
        "category": "CONNECTION_POOL_EXHAUSTED",
        "level": "warning",
        "keywords": (
            "connection is not available",
            "timeout waiting for connection",
            "connection pool waiting",
            "pool exhausted",
            "too many connections",
        ),
        "title": "检测到连接池等待或耗尽",
    },
    {
        "category": "SEATA_TRANSACTION",
        "level": "warning",
        "keywords": ("global transaction rollback", "branch rollback", "lockkey conflict", "seata transaction"),
        "title": "检测到 Seata 事务回滚或锁冲突",
    },
    {
        "category": "RABBITMQ_DELIVERY",
        "level": "warning",
        "keywords": (
            "channel shutdown",
            "basic.nack",
            "consumer timeout",
            "messages_unacknowledged",
            "queue overflow",
        ),
        "title": "检测到 RabbitMQ 投递或消费异常",
    },
    {
        "category": "REDIS_ERROR",
        "level": "warning",
        "keywords": ("redis command timed out", "redis connection", "jedisconnectionexception", "redisson timeout"),
        "title": "检测到 Redis 连接或命令异常",
    },
    {
        "category": "TIMEOUT",
        "level": "warning",
        "keywords": ("timed out", "timeout", "read timed out", "connect timed out", "deadline exceeded"),
        "title": "检测到调用超时",
    },
    {
        "category": "GENERIC_ERROR",
        "level": "warning",
        "patterns": (r"\bfatal\b", r"\berror\b", r"\bexception\b", r"\bfailed\b", r"\bfailure\b"),
        "title": "检测到未分类错误",
    },
    {
        "category": "GENERIC_WARNING",
        "level": "info",
        "patterns": (r"\bwarn\b", r"\bwarning\b"),
        "title": "检测到未分类告警",
    },
)

ANSI_ESCAPE = re.compile(r"\x1b\[[0-?]*[ -/]*[@-~]")
SENSITIVE_VALUE = re.compile(
    r"(?i)(password|passwd|token|secret|api[-_]?key|authorization)\s*([=:])\s*([^\s,;]+)"
)


def analyze_logs(state: AnalysisState) -> dict[str, list[dict[str, Any]]]:
    """分析最多 200 条日志，按异常类型聚合并生成证据。"""
    raw_logs = state.get("logs") or []
    logs = [_sanitize_log(line) for line in raw_logs[:200] if isinstance(line, str) and line.strip()]
    if not logs:
        return {
            "log_findings": [],
            "evidence": [{
                "id": "log:unavailable",
                "source": "logs",
                "level": "warning",
                "message": "本次实验没有可用日志，日志维度的诊断置信度需要降低",
                "data": {"totalLines": 0, "matchedLines": 0},
            }],
        }

    grouped: dict[str, dict[str, Any]] = {}
    matched_lines = 0
    for line in logs:
        rule = _match_rule(line)
        if rule is None:
            continue
        matched_lines += 1
        category = str(rule["category"])
        finding = grouped.setdefault(category, {
            "category": category,
            "title": rule["title"],
            "level": rule["level"],
            "count": 0,
            "samples": [],
        })
        finding["count"] += 1
        if len(finding["samples"]) < 3:
            finding["samples"].append(line[:500])

    findings = list(grouped.values())
    if not findings:
        return {
            "log_findings": [],
            "evidence": [{
                "id": "log:no-recognized-anomaly",
                "source": "logs",
                "level": "info",
                "message": f"已检查 {len(logs)} 条日志，未识别到已知异常模式",
                "data": {"totalLines": len(logs), "matchedLines": 0},
            }],
        }

    evidence = [
        {
            "id": f"log:{finding['category'].lower()}",
            "source": "logs",
            "level": finding["level"],
            "message": f"{finding['title']}，共 {finding['count']} 条",
            "data": {
                "category": finding["category"],
                "count": finding["count"],
                "samples": finding["samples"],
                "totalLines": len(logs),
                "matchedLines": matched_lines,
            },
        }
        for finding in findings
    ]
    return {
        "log_findings": findings,
        "evidence": evidence,
    }


def _match_rule(line: str) -> dict[str, Any] | None:
    normalized = line.lower()
    for rule in LOG_RULES:
        keywords = rule.get("keywords", ())
        if any(keyword in normalized for keyword in keywords):
            return rule
        patterns = rule.get("patterns", ())
        if any(re.search(pattern, normalized) for pattern in patterns):
            return rule
    return None


def _sanitize_log(line: str) -> str:
    clean_line = ANSI_ESCAPE.sub("", line).strip()[:2000]
    return SENSITIVE_VALUE.sub(lambda match: f"{match.group(1)}{match.group(2)}[REDACTED]", clean_line)
