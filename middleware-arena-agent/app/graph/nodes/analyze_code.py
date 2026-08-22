"""代码 Diff 分析节点。

1. 分析当前版本代码与基线版本 Diff。
2. 提取可能影响性能的事务、远程调用、缓存、查询、并发和配置变化。
3. 只输出风险信号，不把单行代码变化直接当成已经发生的故障。
"""

import re
from typing import Any

from app.graph.state import AnalysisState


CODE_RULES = (
    {
        "category": "TRANSACTION_BOUNDARY",
        "level": "warning",
        "patterns": (r"@transactional\b", r"@globaltransactional\b"),
        "message": "事务边界发生变化，需检查锁持有时间和事务内远程调用",
    },
    {
        "category": "BLOCKING_CALL",
        "level": "warning",
        "patterns": (r"thread\.sleep\s*\(", r"\.join\s*\(", r"future\.get\s*\("),
        "message": "出现阻塞等待调用，可能增加线程占用和请求延迟",
    },
    {
        "category": "REDIS_FULL_SCAN",
        "level": "critical",
        "patterns": (r"redistemplate\.keys\s*\(", r"\bkeys\s+\*", r"commands\.keys\s*\("),
        "message": "出现 Redis 全量 KEYS 扫描，数据量增大时可能阻塞实例",
    },
    {
        "category": "ELASTICSEARCH_DEEP_PAGING",
        "level": "warning",
        "patterns": (r"from\s*\+\s*size", r"\.from\s*\(", r"search_after", r"max_result_window"),
        "message": "Elasticsearch 分页方式发生变化，需检查深分页开销",
    },
    {
        "category": "SQL_FULL_SCAN",
        "level": "warning",
        "patterns": (r"select\s+\*\s+from", r"like\s+['\"]%", r"order\s+by\s+rand\s*\("),
        "message": "SQL 变化包含潜在全表扫描或高开销查询模式",
    },
    {
        "category": "MAPPING_CHANGE",
        "level": "info",
        "patterns": (r"@tablefield\b", r"resultmap\s*=", r"<result\s+column=", r"column\s*=\s*['\"]"),
        "message": "数据库字段或结果映射发生变化，需确认实体、Mapper 与表结构一致",
    },
    {
        "category": "RABBITMQ_DELIVERY_CONFIG",
        "level": "info",
        "patterns": (r"prefetch", r"acknowledge-mode", r"basicack\s*\(", r"basicnack\s*\(", r"publisher-confirm"),
        "message": "RabbitMQ ACK、prefetch 或发布确认配置发生变化",
    },
    {
        "category": "POOL_OR_TIMEOUT_CONFIG",
        "level": "info",
        "patterns": (
            r"maximum-pool-size",
            r"max-active",
            r"max-connections",
            r"connection-timeout",
            r"read-timeout",
            r"retry",
        ),
        "message": "连接池、超时或重试参数发生变化，需结合压测指标确认影响",
    },
    {
        "category": "LOCK_CONTENTION",
        "level": "warning",
        "patterns": (r"\bsynchronized\s*\(", r"reentrantlock", r"\.lock\s*\(\)"),
        "message": "新增或调整了显式锁，可能引入锁竞争",
    },
    {
        "category": "UNBOUNDED_EXECUTOR",
        "level": "critical",
        "patterns": (r"newcachedthreadpool\s*\(", r"new\s+linkedblockingqueue\s*<>\s*\(\s*\)"),
        "message": "出现无界线程池或无界队列，流量突增时可能耗尽资源",
    },
)

LOOP_PATTERN = re.compile(r"\b(for|while)\s*\(", re.IGNORECASE)
REMOTE_CALL_PATTERN = re.compile(
    r"(feign|resttemplate|webclient|redistemplate|rabbittemplate|\b\w+client\.\w+\s*\(|\.select(one|list|byid)\s*\()",
    re.IGNORECASE,
)
TRANSACTION_PATTERN = re.compile(r"@(global)?transactional\b", re.IGNORECASE)
SENSITIVE_VALUE = re.compile(
    r"(?i)(password|passwd|token|secret|api[-_]?key)\s*([=:])\s*([^\s,;]+)"
)


def analyze_code(state: AnalysisState) -> dict[str, list[dict[str, Any]]]:
    """分析最多 100 个变更文件，并生成结构化代码风险证据。"""
    code_diff = state.get("code_diff") or []
    changed_files = [item for item in code_diff[:100] if isinstance(item, dict)]
    if not changed_files:
        return {
            "code_findings": [],
            "evidence": [{
                "id": "code:no-diff",
                "source": "code_diff",
                "level": "info",
                "message": "当前实验没有可用的代码 Diff",
                "data": {"changedFiles": 0},
            }],
        }

    findings: list[dict[str, Any]] = []
    for file_diff in changed_files:
        if str(file_diff.get("changeType", "")).upper() == "UNCHANGED":
            continue
        path = str(file_diff.get("path") or "unknown")
        diff_lines = [line for line in (file_diff.get("diffLines") or [])[:500] if isinstance(line, dict)]
        added_contents: list[str] = []

        for diff_line in diff_lines:
            content = str(diff_line.get("content") or "")
            if not content.strip():
                continue
            change_type = str(diff_line.get("type") or "").upper()
            line_number = diff_line.get("newLineNo") if change_type == "ADD" else diff_line.get("oldLineNo")
            if change_type == "ADD":
                added_contents.append(content)

            for rule in CODE_RULES:
                if not any(re.search(pattern, content, re.IGNORECASE) for pattern in rule["patterns"]):
                    continue
                findings.append({
                    "category": rule["category"],
                    "level": rule["level"] if change_type == "ADD" else "info",
                    "assessment": "POTENTIAL_RISK" if change_type == "ADD" else "REMOVED_SIGNAL",
                    "path": path,
                    "line": line_number,
                    "changeType": change_type,
                    "message": rule["message"],
                    "snippet": _sanitize_snippet(content),
                })
                if len(findings) >= 100:
                    break
            if len(findings) >= 100:
                break

        added_text = "\n".join(added_contents)
        if LOOP_PATTERN.search(added_text) and REMOTE_CALL_PATTERN.search(added_text):
            findings.append({
                "category": "LOOP_REMOTE_CALL",
                "level": "warning",
                "assessment": "REVIEW_REQUIRED",
                "path": path,
                "line": None,
                "changeType": "ADD",
                "message": "同一变更文件同时新增循环和远程/数据访问调用，需确认是否形成 N+1 调用",
                "snippet": "",
            })
        if TRANSACTION_PATTERN.search(added_text) and REMOTE_CALL_PATTERN.search(added_text):
            findings.append({
                "category": "TRANSACTION_REMOTE_CALL",
                "level": "warning",
                "assessment": "REVIEW_REQUIRED",
                "path": path,
                "line": None,
                "changeType": "ADD",
                "message": "同一变更文件同时调整事务和远程调用，需确认外部调用是否位于事务内部",
                "snippet": "",
            })
        if len(findings) >= 100:
            findings = findings[:100]
            break

    if not findings:
        return {
            "code_findings": [],
            "evidence": [{
                "id": "code:no-recognized-risk",
                "source": "code_diff",
                "level": "info",
                "message": f"已检查 {len(changed_files)} 个变更文件，未识别到已知性能风险信号",
                "data": {"changedFiles": len(changed_files)},
            }],
        }

    evidence = [
        {
            "id": f"code:{index}:{finding['category'].lower()}",
            "source": "code_diff",
            "level": finding["level"],
            "message": f"{finding['path']}：{finding['message']}",
            "data": {
                "category": finding["category"],
                "path": finding["path"],
                "line": finding["line"],
                "changeType": finding["changeType"],
                "assessment": finding["assessment"],
                "snippet": finding["snippet"],
            },
        }
        for index, finding in enumerate(findings, start=1)
    ]
    return {
        "code_findings": findings,
        "evidence": evidence,
    }


def _sanitize_snippet(content: str) -> str:
    snippet = content.strip()[:500]
    return SENSITIVE_VALUE.sub(lambda match: f"{match.group(1)}{match.group(2)}[REDACTED]", snippet)


# TODO[LLM 语义补充分析]:
# 1. 收集未命中 CODE_RULES 的重要 ADD 代码行，限制文件数、行数和总字符数后交给 LLM。
# 2. 使用 Pydantic structured output 返回 category/level/message/path/line/evidence，禁止解析自由文本 JSON。
# 3. 将 LLM 发现与规则发现去重；LLM 不可用时保留规则结果，并降低“未发现风险”结论的置信度。
