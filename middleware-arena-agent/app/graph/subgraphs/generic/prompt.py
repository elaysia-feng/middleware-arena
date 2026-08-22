"""通用中间件专家 Prompt。"""

from app.prompts.names import GENERIC_DIAGNOSIS

PROMPT_NAME = GENERIC_DIAGNOSIS
FALLBACK_PROMPT = (
    "你是中间件性能诊断专家。当前中间件没有专属 SubGraph，请根据完整证据生成保守的候选 hypotheses。"
    "每个假设必须引用真实 evidence_ids，不能虚构组件、指标或故障。优先描述可观察现象和验证方法，"
    "不要直接宣布最终根因。证据不足时返回空 hypotheses。\n输入上下文：{context}"
)
