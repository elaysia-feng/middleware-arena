"""Redis 专家结构化 LLM 诊断节点。"""

import json
from typing import Any

from app.clients.langfuse import build_langfuse_run_config
from app.clients.llm import get_chat_model
from app.graph.state import AnalysisState
from app.graph.subgraphs.redis.evidence import (
    build_tool_evidence,
    collect_all_evidence,
    is_redis_signal,
)
from app.graph.subgraphs.redis.schemas import RedisDiagnosisOutput, RedisHypothesisOutput
from app.graph.subgraphs.redis.prompt import FALLBACK_PROMPT, PROMPT_NAME
from app.prompts.manager import get_prompt


def diagnose_redis(
    state: AnalysisState,
    *,
    chat_model: Any | None = None,
) -> dict[str, Any]:
    """调用结构化 LLM 形成 Redis 候选假设，并校验证据引用。"""
    evidence = collect_all_evidence(state)
    tool_evidence = build_tool_evidence(state.get("redis_diagnostics") or {})
    evidence.extend(tool_evidence)
    evidence_by_id = {str(item.get("id")): item for item in evidence if item.get("id")}

    prompt = get_prompt(
        PROMPT_NAME,
        variables={"context": _build_llm_context(state, evidence)},
        fallback=FALLBACK_PROMPT,
    )

    llm_error: str | None = None
    try:
        model = chat_model or get_chat_model()
        structured_model = model.with_structured_output(
            RedisDiagnosisOutput,
            method="function_calling",
        )
        raw_result = structured_model.invoke(
            prompt.content,
            config=build_langfuse_run_config(
                run_name="redis-expert-diagnosis",
                metadata={
                    "analysisId": state.get("analysis_id"),
                    "taskId": state.get("task_id"),
                    "middlewareType": "REDIS",
                    "promptName": PROMPT_NAME,
                    "promptVersion": prompt.version,
                    "promptSource": prompt.source,
                },
            ),
        )
        llm_result = (
            raw_result
            if isinstance(raw_result, RedisDiagnosisOutput)
            else RedisDiagnosisOutput.model_validate(raw_result)
        )
        hypotheses = _validated_hypotheses(llm_result.hypotheses, evidence_by_id)
        summary = llm_result.summary
        limitations = list(llm_result.limitations)
        analysis_source = "llm"
    except Exception as error:
        llm_error = type(error).__name__
        hypotheses = _rule_fallback_hypotheses(evidence)
        summary = "LLM 不可用，Redis 专家已根据确定性规则生成候选假设"
        limitations = ["当前结果未经过 LLM 语义关联分析"]
        analysis_source = "rule_fallback"

    diagnostics = state.get("redis_diagnostics") or {}
    limitations.extend(str(item) for item in diagnostics.get("warnings", [])[:5])
    if not hypotheses:
        limitations.append("没有足够的 Redis 专项证据形成候选假设")

    expert_result = {
        "expert": "REDIS",
        "status": "completed" if hypotheses else "insufficient_evidence",
        "summary": summary,
        "analysisSource": analysis_source,
        "promptSource": prompt.source,
        "promptVersion": prompt.version,
        "llmError": llm_error,
        "hypothesisCount": len(hypotheses),
        "limitations": list(dict.fromkeys(limitations))[:8],
    }
    expert_evidence = [
        {
            "id": f"redis:expert:{item['type'].lower()}",
            "source": "redis_expert",
            "level": "warning" if item["confidence"] >= 0.6 else "info",
            "message": item["summary"],
            "data": {
                "hypothesisId": item["id"],
                "confidence": item["confidence"],
                "evidenceIds": item["evidence_ids"],
            },
        }
        for item in hypotheses
    ]

    return {
        "redis_expert_result": expert_result,
        "hypotheses": hypotheses,
        "evidence": [*tool_evidence, *expert_evidence],
    }


def _build_llm_context(state: AnalysisState, evidence: list[dict[str, Any]]) -> str:
    context = {
        "taskId": state.get("task_id"),
        "config": state.get("config", {}),
        "metrics": state.get("metrics", {}),
        "baselineMetrics": state.get("baseline_metrics", {}),
        "metricFindings": state.get("metric_findings", [])[:20],
        "logFindings": state.get("log_findings", [])[:20],
        "codeFindings": state.get("code_findings", [])[:30],
        "similarExperiments": state.get("similar_experiments", [])[:5],
        "allEvidence": evidence[:80],
        "redisSignals": state.get("redis_signals", [])[:30],
        "redisDiagnostics": state.get("redis_diagnostics", {}),
    }
    return json.dumps(context, ensure_ascii=False, default=str)[:30000]


def _validated_hypotheses(
    hypotheses: list[RedisHypothesisOutput],
    evidence_by_id: dict[str, dict[str, Any]],
) -> list[dict[str, Any]]:
    validated: list[dict[str, Any]] = []
    for index, item in enumerate(hypotheses, start=1):
        evidence_ids = list(dict.fromkeys(
            evidence_id for evidence_id in item.evidence_ids if evidence_id in evidence_by_id
        ))
        if not evidence_ids:
            continue
        hypothesis_type = item.type.strip().upper().replace(" ", "_")
        validated.append({
            "id": f"redis:{hypothesis_type.lower()}:{index}",
            "expert": "REDIS",
            "type": hypothesis_type,
            "title": item.title,
            "summary": item.summary,
            "confidence": min(item.confidence, 0.95),
            "evidence_ids": evidence_ids,
            "verification": item.verification,
            "suggestions": item.suggestions,
        })
    return validated


def _rule_fallback_hypotheses(evidence: list[dict[str, Any]]) -> list[dict[str, Any]]:
    rule_definitions = (
        ("REDIS_FULL_SCAN", "REDIS_KEYS_FULL_SCAN", "Redis 全量扫描风险", "发现 KEYS 或全量扫描相关证据", 0.72),
        ("LOOP_REMOTE_CALL", "REDIS_N_PLUS_ONE", "Redis N+1 调用风险", "循环和远程访问组合可能形成大量 Redis 往返", 0.60),
        ("CONNECTION_POOL_EXHAUSTED", "REDIS_CONNECTION_POOL", "Redis 连接池压力", "连接池等待或耗尽可能放大请求延迟", 0.65),
        ("REDIS_ERROR", "REDIS_TIMEOUT_OR_CONNECTION", "Redis 超时或连接异常", "日志包含 Redis 超时或连接异常", 0.68),
    )
    hypotheses: list[dict[str, Any]] = []
    for source_category, hypothesis_type, title, summary, confidence in rule_definitions:
        evidence_ids = [
            str(item.get("id"))
            for item in evidence
            if str((item.get("data") or {}).get("category") or "").upper() == source_category
            and is_redis_signal(item)
            and item.get("id")
        ]
        if not evidence_ids:
            continue
        hypotheses.append({
            "id": f"redis:{hypothesis_type.lower()}",
            "expert": "REDIS",
            "type": hypothesis_type,
            "title": title,
            "summary": summary,
            "confidence": confidence,
            "evidence_ids": evidence_ids[:8],
            "verification": ["结合 Redis SlowLog、命令统计和实例监控进一步确认"],
            "suggestions": [],
        })
    return hypotheses[:5]
