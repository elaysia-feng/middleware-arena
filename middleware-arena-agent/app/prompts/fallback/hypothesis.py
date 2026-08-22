"""瓶颈假设生成节点的本地 fallback Prompt。"""

from app.prompts.names import HYPOTHESIS_GENERATOR

PROMPT_NAME = HYPOTHESIS_GENERATOR
FALLBACK_PROMPT = (
    "你负责把中间件专家候选判断整理成可裁决的瓶颈假设。"
    "可以合并重复判断和建立跨证据关联，但不能创造输入中不存在的事实。"
    "每个 hypothesis 必须引用真实 evidence_ids；相关性不能写成确定因果；最多返回 8 个。"
    "证据不足时返回空 hypotheses 并说明 limitations。\n输入上下文：{context}"
)
