"""根据已确认瓶颈生成可人工审核的候选 Patch。"""

import json
from typing import Any

from app.clients.langfuse import build_langfuse_run_config
from app.clients.llm import get_chat_model
from app.graph.schemas import PatchGenerationOutput
from app.graph.state import AnalysisState
from app.prompts.fallback.patch import FALLBACK_PROMPT, PROMPT_NAME
from app.prompts.manager import get_prompt


def generate_patch(
    state: AnalysisState,
    *,
    chat_model: Any | None = None,
) -> dict[str, Any]:
    """生成最小候选修改，但不写文件、不创建版本，也不触发重跑。"""
    editable_files = _editable_files(state.get("files", []))
    if not editable_files:
        return _empty_result("实验版本没有可编辑文件，已跳过候选 Patch 生成")

    judgement = state.get("judgement", {})
    if judgement.get("status") != "CONFIRMED":
        return _empty_result("瓶颈尚未确认，不能生成可能误导用户的代码 Patch")

    prompt = get_prompt(
        PROMPT_NAME,
        variables={"context": _context(state, editable_files)},
        fallback=FALLBACK_PROMPT,
    )
    try:
        model = chat_model or get_chat_model()
        structured_model = model.with_structured_output(
            PatchGenerationOutput,
            method="function_calling",
        )
        result = structured_model.invoke(
            prompt.content,
            config=build_langfuse_run_config(
                run_name="generate-candidate-patch",
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
            if isinstance(result, PatchGenerationOutput)
            else PatchGenerationOutput.model_validate(result)
        )
        patches = _validate_patches(
            output,
            allowed_paths=set(editable_files),
            allowed_evidence_ids=set(judgement.get("evidenceIds", [])),
        )
        return {
            "patches": patches,
            "patch_summary": {
                "status": "ready" if patches else "not_generated",
                "summary": output.summary,
                "validationSteps": output.validation_steps,
                "limitations": output.limitations,
                "source": "llm",
                "promptSource": prompt.source,
                "promptVersion": prompt.version,
            },
            "next_action": "HUMAN_REVIEW" if patches else "REVIEW_REPORT",
        }
    except Exception as error:
        result = _empty_result("MiniMax 调用失败，未生成未经审核的兜底 Patch")
        result["patch_summary"]["llmError"] = type(error).__name__
        result["patch_summary"]["promptSource"] = prompt.source
        result["patch_summary"]["promptVersion"] = prompt.version
        return result


def _editable_files(files: list[dict[str, Any]]) -> dict[str, dict[str, Any]]:
    """筛选明确允许 Agent 提议修改的实验文件。"""
    editable: dict[str, dict[str, Any]] = {}
    for item in files:
        if not isinstance(item, dict) or item.get("editable") is not True:
            continue
        path = str(item.get("path") or item.get("filePath") or "").strip()
        if path:
            editable[path] = item
    return editable


def _context(
    state: AnalysisState,
    editable_files: dict[str, dict[str, Any]],
) -> str:
    """只向模型发送本次修改所需的代码和已裁决证据。"""
    payload = {
        "taskId": state.get("task_id"),
        "middlewareType": state.get("middleware_type"),
        "bottleneck": state.get("bottleneck", {}),
        "judgement": state.get("judgement", {}),
        "editablePaths": list(editable_files),
        "files": list(editable_files.values()),
        "evidence": state.get("merged_evidence", [])[:100],
    }
    return json.dumps(
        payload,
        ensure_ascii=False,
        default=str,
    )[:50000]


def _validate_patches(
    output: PatchGenerationOutput,
    *,
    allowed_paths: set[str],
    allowed_evidence_ids: set[str],
) -> list[dict[str, Any]]:
    """剔除越权路径、伪 Diff 和没有裁决证据支持的修改。"""
    validated: list[dict[str, Any]] = []
    for item in output.patches:
        if item.path not in allowed_paths:
            continue
        if "--- " not in item.unified_diff or "+++ " not in item.unified_diff:
            continue

        evidence_ids = [
            evidence_id
            for evidence_id in dict.fromkeys(item.evidence_ids)
            if evidence_id in allowed_evidence_ids
        ]
        if not evidence_ids:
            continue
        validated.append(
            {
                "path": item.path,
                "summary": item.summary,
                "unifiedDiff": item.unified_diff,
                "evidenceIds": evidence_ids,
                "risks": item.risks,
                "status": "CANDIDATE",
            }
        )
    return validated


def _empty_result(reason: str) -> dict[str, Any]:
    """返回显式未生成状态，避免上层把空 Patch 当成成功结果。"""
    return {
        "patches": [],
        "patch_summary": {
            "status": "not_generated",
            "summary": reason,
            "validationSteps": [],
            "limitations": [reason],
            "source": "rule_guard",
        },
        "next_action": "REVIEW_REPORT",
    }
