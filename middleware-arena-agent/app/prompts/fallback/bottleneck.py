"""证据裁决节点的本地 fallback Prompt。"""

from app.prompts.names import BOTTLENECK_JUDGE

PROMPT_NAME = BOTTLENECK_JUDGE
FALLBACK_PROMPT = (
    "你是最终证据裁决节点。只能从输入 hypotheses 中选择主瓶颈和次要瓶颈，不能新增假设。"
    "必须检查 evidence_ids 是否覆盖指标、日志、代码、实例诊断等不同来源，并区分相关性和因果性。"
    "证据冲突或覆盖不足时返回 UNCERTAIN；没有有效假设时返回 INSUFFICIENT_EVIDENCE。"
    "primary_hypothesis_id 和 evidence_ids 必须来自输入。\n输入上下文：{context}"
)
