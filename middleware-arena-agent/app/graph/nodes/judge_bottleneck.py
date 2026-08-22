"""根据证据完整度裁决主瓶颈和次要瓶颈。"""

import json
from typing import Any

from app.clients.langfuse import build_langfuse_run_config
from app.clients.llm import get_chat_model
from app.graph.schemas import BottleneckJudgeOutput
from app.graph.state import AnalysisState
from app.prompts.fallback.bottleneck import FALLBACK_PROMPT, PROMPT_NAME
from app.prompts.manager import get_prompt


def judge_bottleneck(state: AnalysisState, *, chat_model: Any | None = None) -> dict[str, Any]:
    """裁决主瓶颈、置信度和建议，并限制结论必须引用真实证据。

    单一证据来源不能得到 CONFIRMED；LLM 不可用时按证据覆盖度规则降级。
    """
    hypotheses = [item for item in state.get("ranked_hypotheses", []) if isinstance(item, dict)]
    evidence = _all_evidence(state)
    hypotheses_by_id = {str(item["id"]): item for item in hypotheses if item.get("id")}
    evidence_by_id = {str(item["id"]): item for item in evidence if item.get("id")}
    if not hypotheses_by_id:
        judgement = _insufficient_evidence_judgement()
        return _build_result(judgement, hypotheses_by_id, source="rule_fallback")

    prompt = get_prompt(
        PROMPT_NAME,
        variables={"context": _context(state, hypotheses, evidence)},
        fallback=FALLBACK_PROMPT,
    )
    llm_error: str | None = None
    try:
        model = chat_model or get_chat_model()
        structured_model = model.with_structured_output(
            BottleneckJudgeOutput,
            method="function_calling",
        )
        result = structured_model.invoke(
            prompt.content,
            config=build_langfuse_run_config(
                run_name="judge-bottleneck",
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
        output = result if isinstance(result, BottleneckJudgeOutput) else BottleneckJudgeOutput.model_validate(result)
        judgement = _validate(output, hypotheses_by_id, evidence_by_id)
        source = "llm"
    except Exception as error:
        llm_error = type(error).__name__
        judgement = _fallback(hypotheses, evidence_by_id)
        source = "rule_fallback"
    return _build_result(
        judgement,
        hypotheses_by_id,
        source=source,
        prompt_source=prompt.source,
        prompt_version=prompt.version,
        llm_error=llm_error,
    )


def _all_evidence(state: AnalysisState) -> list[dict[str, Any]]:
    merged = {}
    for index, item in enumerate([*(state.get("merged_evidence") or []), *(state.get("evidence") or [])]):
        if isinstance(item, dict):
            merged[str(item.get("id") or f"anonymous:{index}")] = item
    return list(merged.values())


def _context(
    state: AnalysisState,
    hypotheses: list[dict[str, Any]],
    evidence: list[dict[str, Any]],
) -> str:
    payload = {
        "taskId": state.get("task_id"),
        "middlewareType": state.get("middleware_type"),
        "hypotheses": hypotheses[:8],
        "evidence": evidence[:100],
        "evidenceSummary": state.get("evidence_summary", {}),
        "hypothesisSummary": state.get("hypothesis_summary", {}),
    }
    return json.dumps(payload, ensure_ascii=False, default=str)[:35000]


def _validate(
    output: BottleneckJudgeOutput,
    hypotheses_by_id: dict[str, Any],
    evidence_by_id: dict[str, Any],
) -> dict[str, Any]:
    primary_id = output.primary_hypothesis_id if output.primary_hypothesis_id in hypotheses_by_id else None
    primary = hypotheses_by_id.get(str(primary_id))
    primary_evidence_ids = set(primary.get("evidence_ids", [])) if primary else set()
    evidence_ids = list(dict.fromkeys(
        value
        for value in output.evidence_ids
        if value in evidence_by_id and value in primary_evidence_ids
    ))
    evidence_sources = {
        str(evidence_by_id[value].get("source") or "unknown")
        for value in evidence_ids
    }
    status = output.status
    confidence = min(output.confidence, 0.95) if primary_id else 0.0
    limitations = list(output.limitations)
    if primary_id is None or not evidence_ids:
        status = "INSUFFICIENT_EVIDENCE" if not hypotheses_by_id else "UNCERTAIN"
        confidence = 0.0 if primary_id is None else min(confidence, 0.49)
        limitations.append("主假设缺少可验证的直接证据引用")
    elif status == "CONFIRMED" and len(evidence_sources) < 2:
        status = "UNCERTAIN"
        confidence = min(confidence, 0.69)
        limitations.append("主假设只有单一证据来源，不能判定为已确认")
    return {
        "status": status,
        "primaryHypothesisId": primary_id,
        "secondaryHypothesisIds": [
            value
            for value in output.secondary_hypothesis_ids
            if value in hypotheses_by_id and value != primary_id
        ][:3],
        "confidence": confidence,
        "reason": output.reason,
        "evidenceIds": evidence_ids,
        "evidenceSources": sorted(evidence_sources),
        "suggestions": output.suggestions,
        "limitations": list(dict.fromkeys(limitations))[:8],
    }


def _fallback(hypotheses: list[dict[str, Any]], evidence_by_id: dict[str, Any]) -> dict[str, Any]:
    valid = []
    for item in hypotheses:
        evidence_ids = [value for value in item.get("evidence_ids", []) if value in evidence_by_id]
        if evidence_ids:
            source_count = len(
                {
                    evidence_by_id[value].get("source")
                    for value in evidence_ids
                }
            )
            valid.append(({**item, "evidence_ids": evidence_ids}, source_count))
    if not valid:
        return _insufficient_evidence_judgement()
    valid.sort(
        key=lambda pair: (
            float(pair[0].get("confidence") or 0),
            pair[1],
            len(pair[0]["evidence_ids"]),
        ),
        reverse=True,
    )
    primary, source_count = valid[0]
    confidence = min(float(primary.get("confidence") or 0), 0.85)
    status = "CONFIRMED" if confidence >= 0.75 and source_count >= 2 else "UNCERTAIN"
    return {
        "status": status,
        "primaryHypothesisId": primary["id"],
        "secondaryHypothesisIds": [item[0]["id"] for item in valid[1:4]],
        "confidence": confidence,
        "reason": "按专家置信度、证据来源覆盖和证据数量排序",
        "evidenceIds": primary["evidence_ids"],
        "evidenceSources": sorted({
            str(evidence_by_id[value].get("source") or "unknown")
            for value in primary["evidence_ids"]
        }),
        "suggestions": primary.get("suggestions", []),
        "limitations": [] if status == "CONFIRMED" else ["证据来源覆盖不足，当前结论仍需验证"],
    }


def _insufficient_evidence_judgement() -> dict[str, Any]:
    return {
        "status": "INSUFFICIENT_EVIDENCE",
        "primaryHypothesisId": None,
        "secondaryHypothesisIds": [],
        "confidence": 0.0,
        "reason": "没有带有效证据引用的瓶颈假设",
        "evidenceIds": [],
        "evidenceSources": [],
        "suggestions": [],
        "limitations": ["需要补充实验指标、日志、代码或实例诊断证据"],
    }


def _build_result(
    judgement: dict[str, Any],
    hypotheses_by_id: dict[str, dict[str, Any]],
    *,
    source: str,
    prompt_source: str | None = None,
    prompt_version: int | None = None,
    llm_error: str | None = None,
) -> dict[str, Any]:
    primary = hypotheses_by_id.get(str(judgement.get("primaryHypothesisId")))
    bottleneck = {
        "status": judgement["status"],
        "primary": primary,
        "secondaryHypothesisIds": judgement["secondaryHypothesisIds"],
        "reason": judgement["reason"],
        "evidenceIds": judgement["evidenceIds"],
        "evidenceSources": judgement.get("evidenceSources", []),
        "limitations": judgement["limitations"],
    }
    return {
        "bottleneck": bottleneck,
        "confidence": judgement["confidence"],
        "suggestions": [{"message": item} for item in judgement["suggestions"]],
        "judgement": {
            **judgement,
            "source": source,
            "promptSource": prompt_source,
            "promptVersion": prompt_version,
            "llmError": llm_error,
        },
    }
