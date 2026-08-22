"""RabbitMQ 专家结构化 LLM 诊断节点。"""

import json
from typing import Any

from app.clients.langfuse import build_langfuse_run_config
from app.clients.llm import get_chat_model
from app.graph.state import AnalysisState
from app.graph.subgraphs.rabbitmq.evidence import (
    build_tool_evidence,
    collect_all_evidence,
    is_rabbitmq_signal,
)
from app.graph.subgraphs.rabbitmq.prompt import FALLBACK_PROMPT, PROMPT_NAME
from app.graph.subgraphs.rabbitmq.schemas import (
    RabbitMqDiagnosisOutput,
    RabbitMqHypothesisOutput,
)
from app.prompts.manager import get_prompt


def diagnose_rabbitmq(
    state: AnalysisState,
    *,
    chat_model: Any | None = None,
) -> dict[str, Any]:
    """综合通用证据和 Broker 诊断结果，生成 RabbitMQ 专项假设。"""
    evidence = collect_all_evidence(state)
    tool_evidence = build_tool_evidence(
        state.get("rabbitmq_diagnostics") or {}
    )
    evidence.extend(tool_evidence)
    evidence_by_id = {
        str(item["id"]): item
        for item in evidence
        if item.get("id")
    }
    prompt = get_prompt(
        PROMPT_NAME,
        variables={"context": _context(state, evidence)},
        fallback=FALLBACK_PROMPT,
    )
    llm_error: str | None = None
    try:
        model = chat_model or get_chat_model()
        structured_model = model.with_structured_output(
            RabbitMqDiagnosisOutput,
            method="function_calling",
        )
        result = structured_model.invoke(
            prompt.content,
            config=build_langfuse_run_config(
                run_name="rabbitmq-expert-diagnosis",
                metadata=_trace_metadata(
                    state,
                    prompt.version,
                    prompt.source,
                ),
            ),
        )
        output = (
            result
            if isinstance(result, RabbitMqDiagnosisOutput)
            else RabbitMqDiagnosisOutput.model_validate(result)
        )
        hypotheses = _validate(output.hypotheses, evidence_by_id)
        summary = output.summary
        limitations = list(output.limitations)
        source = "llm"
    except Exception as error:
        llm_error = type(error).__name__
        hypotheses = _fallback(evidence)
        summary = "LLM 不可用，已使用 RabbitMQ 规则生成候选假设"
        limitations = ["缺少 LLM 语义分析"]
        source = "rule_fallback"

    diagnostics = state.get("rabbitmq_diagnostics") or {}
    limitations.extend(
        str(item)
        for item in diagnostics.get("warnings", [])[:5]
    )
    if not hypotheses:
        limitations.append("没有足够的 RabbitMQ 专项证据")
    return {
        "rabbitmq_expert_result": {
            "expert": "RABBITMQ",
            "status": (
                "completed" if hypotheses else "insufficient_evidence"
            ),
            "summary": summary,
            "analysisSource": source,
            "promptSource": prompt.source,
            "promptVersion": prompt.version,
            "llmError": llm_error,
            "hypothesisCount": len(hypotheses),
            "limitations": list(dict.fromkeys(limitations))[:8],
        },
        "hypotheses": hypotheses,
        "evidence": [*tool_evidence, *_expert_evidence(hypotheses)],
    }


def _context(
    state: AnalysisState,
    evidence: list[dict[str, Any]],
) -> str:
    payload = {
        "taskId": state.get("task_id"),
        "config": state.get("config", {}),
        "metrics": state.get("metrics", {}),
        "baselineMetrics": state.get("baseline_metrics", {}),
        "allEvidence": evidence[:80],
        "rabbitmqSignals": state.get("rabbitmq_signals", [])[:30],
        "rabbitmqDiagnostics": state.get("rabbitmq_diagnostics", {}),
        "similarExperiments": state.get("similar_experiments", [])[:5],
    }
    return json.dumps(payload, ensure_ascii=False, default=str)[:30000]


def _trace_metadata(
    state: AnalysisState,
    version: int | None,
    source: str,
) -> dict[str, Any]:
    return {
        "analysisId": state.get("analysis_id"),
        "taskId": state.get("task_id"),
        "middlewareType": "RABBITMQ",
        "promptName": PROMPT_NAME,
        "promptVersion": version,
        "promptSource": source,
    }


def _validate(
    items: list[RabbitMqHypothesisOutput],
    evidence_by_id: dict[str, Any],
) -> list[dict[str, Any]]:
    validated = []
    for index, item in enumerate(items, 1):
        evidence_ids = list(
            dict.fromkeys(
                value
                for value in item.evidence_ids
                if value in evidence_by_id
            )
        )
        if not evidence_ids:
            continue
        kind = item.type.strip().upper().replace(" ", "_")
        validated.append(
            {
                "id": f"rabbitmq:{kind.lower()}:{index}",
                "expert": "RABBITMQ",
                "type": kind,
                "title": item.title,
                "summary": item.summary,
                "confidence": min(item.confidence, 0.95),
                "evidence_ids": evidence_ids,
                "verification": item.verification,
                "suggestions": item.suggestions,
            }
        )
    return validated


def _fallback(evidence: list[dict[str, Any]]) -> list[dict[str, Any]]:
    matches = [
        item
        for item in evidence
        if is_rabbitmq_signal(item) and item.get("id")
    ]
    if not matches:
        return []
    return [
        {
            "id": "rabbitmq:delivery_or_backlog",
            "expert": "RABBITMQ",
            "type": "RABBITMQ_DELIVERY_OR_BACKLOG",
            "title": "RabbitMQ 投递或消费异常",
            "summary": "RabbitMQ 证据显示投递、确认或消费链路需要进一步检查",
            "confidence": 0.62,
            "evidence_ids": [str(item["id"]) for item in matches[:8]],
            "verification": [
                "检查 ready、unacked、publish/deliver/ack 速率和消费者数量"
            ],
            "suggestions": [],
        }
    ]


def _expert_evidence(
    hypotheses: list[dict[str, Any]],
) -> list[dict[str, Any]]:
    return [
        {
            "id": f"rabbitmq:expert:{item['type'].lower()}",
            "source": "rabbitmq_expert",
            "level": "warning",
            "message": item["summary"],
            "data": {
                "hypothesisId": item["id"],
                "confidence": item["confidence"],
                "evidenceIds": item["evidence_ids"],
            },
        }
        for item in hypotheses
    ]
