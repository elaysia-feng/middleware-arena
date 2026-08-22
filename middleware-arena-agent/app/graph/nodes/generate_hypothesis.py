"""合并各专家候选判断，生成供 Judge 裁决的瓶颈假设。"""

import json
from typing import Any

from app.clients.langfuse import build_langfuse_run_config
from app.clients.llm import get_chat_model
from app.graph.schemas import GeneratedHypothesis, HypothesisGenerationOutput
from app.graph.state import AnalysisState
from app.prompts.fallback.hypothesis import FALLBACK_PROMPT, PROMPT_NAME
from app.prompts.manager import get_prompt


def generate_hypothesis(state: AnalysisState, *, chat_model: Any | None = None) -> dict[str, Any]:
    """将各专家候选合并、校验并按置信度排序，供 Judge 统一裁决。

    LLM 失败时只复用已有专家候选，不创造输入证据之外的新假设。
    """
    evidence = _all_evidence(state)
    evidence_by_id = {str(item["id"]): item for item in evidence if item.get("id")}
    expert_candidates = [item for item in state.get("hypotheses", []) if isinstance(item, dict)]
    prompt = get_prompt(
        PROMPT_NAME,
        variables={"context": _context(state, evidence, expert_candidates)},
        fallback=FALLBACK_PROMPT,
    )
    llm_error: str | None = None
    try:
        model = chat_model or get_chat_model()
        structured_model = model.with_structured_output(
            HypothesisGenerationOutput,
            method="function_calling",
        )
        result = structured_model.invoke(
            prompt.content,
            config=build_langfuse_run_config(
                run_name="generate-bottleneck-hypotheses",
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
            if isinstance(result, HypothesisGenerationOutput)
            else HypothesisGenerationOutput.model_validate(result)
        )
        hypotheses = _validate(output.hypotheses, evidence_by_id)
        summary, limitations, source = output.summary, list(output.limitations), "llm"
    except Exception as error:
        llm_error = type(error).__name__
        hypotheses = _fallback(expert_candidates, evidence_by_id)
        summary, limitations, source = "LLM 不可用，已按专家候选和证据完整度整理假设", ["未执行跨证据 LLM 关联"], "rule_fallback"
    hypotheses.sort(key=lambda item: item["confidence"], reverse=True)
    return {
        "ranked_hypotheses": hypotheses[:8],
        "hypothesis_summary": {
            "status": "ready" if hypotheses else "insufficient_evidence",
            "summary": summary,
            "source": source,
            "promptSource": prompt.source,
            "promptVersion": prompt.version,
            "llmError": llm_error,
            "count": len(hypotheses),
            "limitations": limitations[:8],
        },
    }


def _all_evidence(state: AnalysisState) -> list[dict[str, Any]]:
    merged = {}
    for index, item in enumerate([*(state.get("merged_evidence") or []), *(state.get("evidence") or [])]):
        if isinstance(item, dict):
            merged[str(item.get("id") or f"anonymous:{index}")] = item
    return list(merged.values())


def _context(
    state: AnalysisState,
    evidence: list[dict[str, Any]],
    candidates: list[dict[str, Any]],
) -> str:
    payload = {
        "taskId": state.get("task_id"),
        "middlewareType": state.get("middleware_type"),
        "expertCandidates": candidates[:20],
        "evidence": evidence[:100],
        "evidenceSummary": state.get("evidence_summary", {}),
    }
    return json.dumps(payload, ensure_ascii=False, default=str)[:35000]


def _validate(items: list[GeneratedHypothesis], evidence_by_id: dict[str, Any]) -> list[dict[str, Any]]:
    validated = []
    for index, item in enumerate(items, 1):
        evidence_ids = list(dict.fromkeys(value for value in item.evidence_ids if value in evidence_by_id))
        if not evidence_ids:
            continue
        kind = item.type.strip().upper().replace(" ", "_")
        validated.append(
            {
                "id": f"hypothesis:{kind.lower()}:{index}",
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


def _fallback(candidates: list[dict[str, Any]], evidence_by_id: dict[str, Any]) -> list[dict[str, Any]]:
    deduplicated = {}
    for candidate in candidates:
        evidence_ids = [value for value in candidate.get("evidence_ids", []) if value in evidence_by_id]
        if not evidence_ids:
            continue
        kind = str(candidate.get("type") or "UNKNOWN").upper()
        normalized = {
            **candidate,
            "id": f"hypothesis:{kind.lower()}",
            "type": kind,
            "evidence_ids": evidence_ids[:10],
            "confidence": min(
                float(candidate.get("confidence") or 0),
                0.85,
            ),
        }
        if kind not in deduplicated or normalized["confidence"] > deduplicated[kind]["confidence"]:
            deduplicated[kind] = normalized
    return list(deduplicated.values())
