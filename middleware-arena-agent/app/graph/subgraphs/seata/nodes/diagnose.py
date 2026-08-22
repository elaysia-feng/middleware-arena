"""Seata 专家结构化 LLM 诊断节点。"""

import json
from typing import Any

from app.clients.langfuse import build_langfuse_run_config
from app.clients.llm import get_chat_model
from app.graph.state import AnalysisState
from app.graph.subgraphs.seata.evidence import build_tool_evidence, collect_all_evidence, is_seata_signal
from app.graph.subgraphs.seata.prompt import FALLBACK_PROMPT, PROMPT_NAME
from app.graph.subgraphs.seata.schemas import SeataDiagnosisOutput, SeataHypothesisOutput
from app.prompts.manager import get_prompt


def diagnose_seata(state: AnalysisState, *, chat_model: Any | None = None) -> dict[str, Any]:
    """综合事务链路与数据库证据生成 Seata 假设，失败时规则降级。"""
    evidence = collect_all_evidence(state)
    tool_evidence = build_tool_evidence(state.get("seata_diagnostics") or {})
    evidence.extend(tool_evidence)
    evidence_by_id = {str(item["id"]): item for item in evidence if item.get("id")}
    prompt = get_prompt(PROMPT_NAME, variables={"context": _context(state, evidence)}, fallback=FALLBACK_PROMPT)
    llm_error: str | None = None
    try:
        model = chat_model or get_chat_model()
        structured_model = model.with_structured_output(
            SeataDiagnosisOutput,
            method="function_calling",
        )
        result = structured_model.invoke(
            prompt.content,
            config=build_langfuse_run_config(
                run_name="seata-expert-diagnosis",
                metadata=_trace_metadata(
                    state,
                    prompt.version,
                    prompt.source,
                ),
            ),
        )
        output = result if isinstance(result, SeataDiagnosisOutput) else SeataDiagnosisOutput.model_validate(result)
        hypotheses = _validate(output.hypotheses, evidence_by_id)
        summary, limitations, source = output.summary, list(output.limitations), "llm"
    except Exception as error:
        llm_error = type(error).__name__
        hypotheses = _fallback(evidence)
        summary, limitations, source = "LLM 不可用，已使用 Seata 规则生成候选假设", ["缺少 LLM 语义分析"], "rule_fallback"
    limitations.extend(str(item) for item in (state.get("seata_diagnostics") or {}).get("warnings", [])[:5])
    if not hypotheses:
        limitations.append("没有足够的 Seata 专项证据")
    return {
        "seata_expert_result": {
            "expert": "SEATA",
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
        "evidence": [*tool_evidence, *_expert_evidence(hypotheses)],
    }


def _context(state: AnalysisState, evidence: list[dict[str, Any]]) -> str:
    payload = {
        "taskId": state.get("task_id"),
        "config": state.get("config", {}),
        "metrics": state.get("metrics", {}),
        "baselineMetrics": state.get("baseline_metrics", {}),
        "allEvidence": evidence[:80],
        "seataSignals": state.get("seata_signals", [])[:30],
        "seataDiagnostics": state.get("seata_diagnostics", {}),
        "codeFindings": state.get("code_findings", [])[:30],
        "logFindings": state.get("log_findings", [])[:20],
    }
    return json.dumps(payload, ensure_ascii=False, default=str)[:30000]


def _trace_metadata(state: AnalysisState, version: int | None, source: str) -> dict[str, Any]:
    return {
        "analysisId": state.get("analysis_id"),
        "taskId": state.get("task_id"),
        "middlewareType": "SEATA",
        "promptName": PROMPT_NAME,
        "promptVersion": version,
        "promptSource": source,
    }


def _validate(items: list[SeataHypothesisOutput], evidence_by_id: dict[str, Any]) -> list[dict[str, Any]]:
    validated = []
    for index, item in enumerate(items, 1):
        evidence_ids = list(dict.fromkeys(value for value in item.evidence_ids if value in evidence_by_id))
        if not evidence_ids:
            continue
        kind = item.type.strip().upper().replace(" ", "_")
        validated.append(
            {
                "id": f"seata:{kind.lower()}:{index}",
                "expert": "SEATA",
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
    matches = [item for item in evidence if is_seata_signal(item) and item.get("id")]
    if not matches:
        return []
    return [
        {
            "id": "seata:transaction_chain",
            "expert": "SEATA",
            "type": "SEATA_TRANSACTION_CHAIN",
            "title": "Seata 全局事务链路异常",
            "summary": "现有证据指向全局事务、分支回滚或锁竞争风险",
            "confidence": 0.65,
            "evidence_ids": [str(item["id"]) for item in matches[:8]],
            "verification": ["按 XID 对齐 TC、TM、RM 日志并比较各参与方耗时"],
            "suggestions": [],
        }
    ]


def _expert_evidence(hypotheses: list[dict[str, Any]]) -> list[dict[str, Any]]:
    return [
        {
            "id": f"seata:expert:{item['type'].lower()}",
            "source": "seata_expert",
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
