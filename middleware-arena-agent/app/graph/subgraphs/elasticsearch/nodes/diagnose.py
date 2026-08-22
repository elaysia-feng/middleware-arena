"""Elasticsearch 专家结构化 LLM 诊断节点。"""

import json
from typing import Any

from app.clients.langfuse import build_langfuse_run_config
from app.clients.llm import get_chat_model
from app.graph.state import AnalysisState
from app.graph.subgraphs.elasticsearch.evidence import collect_all_evidence, is_elasticsearch_signal
from app.graph.subgraphs.elasticsearch.prompt import FALLBACK_PROMPT, PROMPT_NAME
from app.graph.subgraphs.elasticsearch.schemas import ElasticsearchDiagnosisOutput, ElasticsearchHypothesisOutput
from app.prompts.manager import get_prompt


def diagnose_elasticsearch(state: AnalysisState, *, chat_model: Any | None = None) -> dict[str, Any]:
    """基于 ES 专项证据生成候选假设，失败时退回确定性规则。"""
    evidence = collect_all_evidence(state)
    evidence_by_id = {str(item["id"]): item for item in evidence if item.get("id")}
    prompt = get_prompt(PROMPT_NAME, variables={"context": _context(state, evidence)}, fallback=FALLBACK_PROMPT)
    llm_error: str | None = None
    try:
        model = chat_model or get_chat_model()
        structured_model = model.with_structured_output(
            ElasticsearchDiagnosisOutput,
            method="function_calling",
        )
        result = structured_model.invoke(
            prompt.content,
            config=build_langfuse_run_config(
                run_name="elasticsearch-expert-diagnosis",
                metadata=_trace_metadata(
                    state,
                    prompt.version,
                    prompt.source,
                ),
            ),
        )
        output = (
            result
            if isinstance(result, ElasticsearchDiagnosisOutput)
            else ElasticsearchDiagnosisOutput.model_validate(result)
        )
        hypotheses = _validate(output.hypotheses, evidence_by_id)
        summary, limitations, source = output.summary, list(output.limitations), "llm"
    except Exception as error:
        llm_error = type(error).__name__
        hypotheses = _fallback(evidence)
        summary, limitations, source = "LLM 不可用，已使用 Elasticsearch 规则生成候选假设", ["缺少 LLM 语义分析"], "rule_fallback"
    limitations.extend((state.get("elasticsearch_diagnostics") or {}).get("warnings", []))
    if not hypotheses:
        limitations.append("没有足够的 Elasticsearch 专项证据")
    return {
        "elasticsearch_expert_result": {
            "expert": "ELASTICSEARCH",
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
        "evidence": _expert_evidence(hypotheses),
    }


def _context(state: AnalysisState, evidence: list[dict[str, Any]]) -> str:
    payload = {
        "taskId": state.get("task_id"),
        "config": state.get("config", {}),
        "metrics": state.get("metrics", {}),
        "baselineMetrics": state.get("baseline_metrics", {}),
        "allEvidence": evidence[:80],
        "elasticsearchSignals": state.get("elasticsearch_signals", [])[:30],
        "codeFindings": state.get("code_findings", [])[:30],
        "logFindings": state.get("log_findings", [])[:20],
        "similarExperiments": state.get("similar_experiments", [])[:5],
    }
    return json.dumps(payload, ensure_ascii=False, default=str)[:30000]


def _trace_metadata(state: AnalysisState, version: int | None, source: str) -> dict[str, Any]:
    return {
        "analysisId": state.get("analysis_id"),
        "taskId": state.get("task_id"),
        "middlewareType": "ELASTICSEARCH",
        "promptName": PROMPT_NAME,
        "promptVersion": version,
        "promptSource": source,
    }


def _validate(items: list[ElasticsearchHypothesisOutput], evidence_by_id: dict[str, Any]) -> list[dict[str, Any]]:
    validated = []
    for index, item in enumerate(items, 1):
        evidence_ids = list(dict.fromkeys(value for value in item.evidence_ids if value in evidence_by_id))
        if not evidence_ids:
            continue
        kind = item.type.strip().upper().replace(" ", "_")
        validated.append(
            {
                "id": f"elasticsearch:{kind.lower()}:{index}",
                "expert": "ELASTICSEARCH",
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
    matches = [item for item in evidence if is_elasticsearch_signal(item) and item.get("id")]
    if not matches:
        return []
    return [
        {
            "id": "elasticsearch:query_or_cluster",
            "expert": "ELASTICSEARCH",
            "type": "ELASTICSEARCH_QUERY_OR_CLUSTER",
            "title": "Elasticsearch 查询或集群压力",
            "summary": "现有证据指向查询方式、熔断、线程池或分片异常",
            "confidence": 0.64,
            "evidence_ids": [str(item["id"]) for item in matches[:8]],
            "verification": [
                "获取 Search Profile、节点统计、分片状态和慢查询日志确认"
            ],
            "suggestions": [],
        }
    ]


def _expert_evidence(hypotheses: list[dict[str, Any]]) -> list[dict[str, Any]]:
    return [
        {
            "id": f"elasticsearch:expert:{item['type'].lower()}",
            "source": "elasticsearch_expert",
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
