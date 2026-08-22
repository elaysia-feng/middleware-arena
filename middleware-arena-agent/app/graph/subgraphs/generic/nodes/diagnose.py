"""未知中间件的通用专家节点。"""

import json
from typing import Any

from app.clients.langfuse import build_langfuse_run_config
from app.clients.llm import get_chat_model
from app.graph.state import AnalysisState
from app.graph.subgraphs.generic.prompt import FALLBACK_PROMPT, PROMPT_NAME
from app.graph.subgraphs.generic.schemas import GenericDiagnosisOutput, GenericHypothesisOutput
from app.prompts.manager import get_prompt


def diagnose_generic(state: AnalysisState, *, chat_model: Any | None = None) -> dict[str, Any]:
    """在没有专项专家时生成保守假设，且不把异常相关性写成确定因果。"""
    evidence = _all_evidence(state)
    evidence_by_id = {str(item["id"]): item for item in evidence if item.get("id")}
    context = _build_context(state, evidence)
    prompt = get_prompt(PROMPT_NAME, variables={"context": context}, fallback=FALLBACK_PROMPT)
    llm_error: str | None = None
    try:
        model = chat_model or get_chat_model()
        structured_model = model.with_structured_output(
            GenericDiagnosisOutput,
            method="function_calling",
        )
        result = structured_model.invoke(
            prompt.content,
            config=build_langfuse_run_config(
                run_name="generic-expert-diagnosis",
                metadata={
                    "analysisId": state.get("analysis_id"),
                    "taskId": state.get("task_id"),
                    "middlewareType": state.get("middleware_type"),
                    "promptName": PROMPT_NAME,
                    "promptVersion": prompt.version,
                    "promptSource": prompt.source,
                },
            ),
        )
        output = (
            result
            if isinstance(result, GenericDiagnosisOutput)
            else GenericDiagnosisOutput.model_validate(result)
        )
        hypotheses = _validate(output.hypotheses, evidence_by_id)
        summary, limitations, source = output.summary, list(output.limitations), "llm"
    except Exception as error:
        llm_error = type(error).__name__
        hypotheses = _fallback(evidence)
        summary = "LLM 不可用，已按证据严重程度生成通用候选假设"
        limitations = ["缺少专属中间件规则和 LLM 语义分析"]
        source = "rule_fallback"
    if not hypotheses:
        limitations.append("没有足够证据形成候选假设")
    return {
        "generic_expert_result": {
            "expert": "GENERIC",
            "status": "completed" if hypotheses else "insufficient_evidence",
            "summary": summary,
            "analysisSource": source,
            "promptSource": prompt.source,
            "promptVersion": prompt.version,
            "llmError": llm_error,
            "hypothesisCount": len(hypotheses),
            "limitations": list(dict.fromkeys(limitations))[:8],
        },
        "hypotheses": hypotheses,
        "evidence": [
            {
                "id": f"generic:expert:{item['type'].lower()}",
                "source": "generic_expert",
                "level": "warning",
                "message": item["summary"],
                "data": {
                    "hypothesisId": item["id"],
                    "confidence": item["confidence"],
                    "evidenceIds": item["evidence_ids"],
                },
            }
            for item in hypotheses
        ],
    }


def _all_evidence(state: AnalysisState) -> list[dict[str, Any]]:
    merged: dict[str, dict[str, Any]] = {}
    combined = [*(state.get("merged_evidence") or []), *(state.get("evidence") or [])]
    for index, item in enumerate(combined):
        if isinstance(item, dict):
            merged[str(item.get("id") or f"anonymous:{index}")] = item
    return list(merged.values())


def _build_context(state: AnalysisState, evidence: list[dict[str, Any]]) -> str:
    context = {
        "taskId": state.get("task_id"),
        "middlewareType": state.get("middleware_type"),
        "config": state.get("config", {}),
        "metrics": state.get("metrics", {}),
        "baselineMetrics": state.get("baseline_metrics", {}),
        "allEvidence": evidence[:80],
        "similarExperiments": state.get("similar_experiments", [])[:5],
    }
    return json.dumps(context, ensure_ascii=False, default=str)[:30000]


def _validate(items: list[GenericHypothesisOutput], evidence_by_id: dict[str, Any]) -> list[dict[str, Any]]:
    validated = []
    for index, item in enumerate(items, 1):
        evidence_ids = list(dict.fromkeys(value for value in item.evidence_ids if value in evidence_by_id))
        if not evidence_ids:
            continue
        kind = item.type.strip().upper().replace(" ", "_")
        validated.append({
            "id": f"generic:{kind.lower()}:{index}",
            "expert": "GENERIC",
            "type": kind,
            "title": item.title,
            "summary": item.summary,
            "confidence": min(item.confidence, 0.80),
            "evidence_ids": evidence_ids,
            "verification": item.verification,
            "suggestions": item.suggestions,
        })
    return validated


def _fallback(evidence: list[dict[str, Any]]) -> list[dict[str, Any]]:
    important = [
        item
        for item in evidence
        if item.get("id") and item.get("level") in {"critical", "warning"}
    ]
    if not important:
        return []
    return [{
        "id": "generic:observed_regression",
        "expert": "GENERIC",
        "type": "OBSERVED_REGRESSION",
        "title": "检测到待确认的性能退化",
        "summary": "现有证据存在异常，但缺少专属中间件诊断数据",
        "confidence": 0.45,
        "evidence_ids": [str(item["id"]) for item in important[:8]],
        "verification": ["补充中间件实例指标和调用链后重新分析"],
        "suggestions": [],
    }]
