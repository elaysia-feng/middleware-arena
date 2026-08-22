"""汇总证据链、瓶颈裁决和候选 Patch，生成最终诊断报告。"""

import json
from typing import Any

from app.clients.langfuse import build_langfuse_run_config
from app.clients.llm import get_chat_model
from app.graph.schemas import ReportGenerationOutput
from app.graph.state import AnalysisState
from app.prompts.fallback.report import FALLBACK_PROMPT, PROMPT_NAME
from app.prompts.manager import get_prompt


def generate_report(
    state: AnalysisState,
    *,
    chat_model: Any | None = None,
) -> dict[str, Any]:
    """优先让 MiniMax 组织报告，调用失败时使用确定性 Markdown 兜底。"""
    prompt = get_prompt(
        PROMPT_NAME,
        variables={"context": _context(state)},
        fallback=FALLBACK_PROMPT,
    )
    try:
        model = chat_model or get_chat_model()
        structured_model = model.with_structured_output(
            ReportGenerationOutput,
            method="function_calling",
        )
        result = structured_model.invoke(
            prompt.content,
            config=build_langfuse_run_config(
                run_name="generate-analysis-report",
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
            if isinstance(result, ReportGenerationOutput)
            else ReportGenerationOutput.model_validate(result)
        )
        return {
            "report": output.markdown,
            "report_summary": {
                "title": output.title,
                "summary": output.summary,
                "source": "llm",
                "promptSource": prompt.source,
                "promptVersion": prompt.version,
            },
        }
    except Exception as error:
        return {
            "report": _fallback_report(state),
            "report_summary": {
                "title": "中间件实验诊断报告",
                "summary": "MiniMax 不可用，已使用结构化状态生成基础报告",
                "source": "rule_fallback",
                "promptSource": prompt.source,
                "promptVersion": prompt.version,
                "llmError": type(error).__name__,
            },
        }


def _context(state: AnalysisState) -> str:
    """控制报告上下文大小，避免原始日志和代码无限进入模型。"""
    payload = {
        "taskId": state.get("task_id"),
        "middlewareType": state.get("middleware_type"),
        "metricFindings": state.get("metric_findings", []),
        "evidenceSummary": state.get("evidence_summary", {}),
        "evidence": state.get("merged_evidence", [])[:100],
        "hypotheses": state.get("ranked_hypotheses", [])[:8],
        "judgement": state.get("judgement", {}),
        "bottleneck": state.get("bottleneck", {}),
        "suggestions": state.get("suggestions", []),
        "patches": state.get("patches", []),
        "patchSummary": state.get("patch_summary", {}),
        "nextAction": state.get("next_action"),
    }
    return json.dumps(
        payload,
        ensure_ascii=False,
        default=str,
    )[:50000]


def _fallback_report(state: AnalysisState) -> str:
    """不调用模型也能输出事实边界清晰的基础报告。"""
    judgement = state.get("judgement", {})
    status = judgement.get("status", "INSUFFICIENT_EVIDENCE")
    confidence = float(state.get("confidence") or 0)
    primary = state.get("bottleneck", {}).get("primary") or {}
    evidence_ids = judgement.get("evidenceIds", [])
    suggestions = state.get("suggestions", [])
    patches = state.get("patches", [])

    lines = [
        "# 中间件实验诊断报告",
        "",
        "## 实验摘要",
        "",
        f"- 任务 ID：{state.get('task_id')}",
        f"- 中间件：{state.get('middleware_type', 'UNKNOWN')}",
        f"- 裁决状态：{status}",
        f"- 置信度：{confidence:.2f}",
        "",
        "## 瓶颈判断",
        "",
        f"- 主要假设：{primary.get('title') or primary.get('type') or '暂无'}",
        f"- 判断依据：{judgement.get('reason') or '证据不足'}",
        f"- 证据 ID：{', '.join(evidence_ids) if evidence_ids else '暂无'}",
        "",
        "## 优化建议",
        "",
    ]
    if suggestions:
        lines.extend(
            f"- {item.get('message', item)}"
            for item in suggestions
        )
    else:
        lines.append("- 先补充可验证证据，再决定是否修改代码。")

    lines.extend(["", "## 候选 Patch", ""])
    if patches:
        lines.extend(
            f"- `{item.get('path')}`：{item.get('summary', '')}"
            for item in patches
        )
        lines.append("- 所有 Patch 均为候选方案，需人工审核后才能应用。")
    else:
        lines.append("- 当前未生成候选 Patch。")

    limitations = judgement.get("limitations", [])
    lines.extend(["", "## 风险与下一步", ""])
    lines.extend(f"- {item}" for item in limitations)
    lines.append(f"- 下一动作：{state.get('next_action', 'REVIEW_REPORT')}")
    return "\n".join(lines)
