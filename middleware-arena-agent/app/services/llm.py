"""资源建议使用的 OpenAI 兼容 LLM 接入。"""

from typing import Any

from langchain_openai import ChatOpenAI
from pydantic import BaseModel, Field

from app.core.config import get_settings


class ResourceAdviceOutput(BaseModel):
    cpus: float = Field(gt=0)
    memory_mb: int = Field(gt=0)
    reason: str = Field(min_length=1, max_length=500)


def request_resource_advice(context: dict[str, Any]) -> dict[str, Any]:
    """让 LLM 给出结构化资源建议；密钥未配置或调用失败时由接口回退到规则计算。"""
    settings = get_settings()
    if not settings.openai_api_key:
        raise RuntimeError("未配置 OPENAI_API_KEY")

    model = ChatOpenAI(
        api_key=settings.openai_api_key,
        base_url=settings.openai_api_base,
        model=settings.openai_model,
        temperature=0,
    ).with_structured_output(ResourceAdviceOutput, method="json_mode")

    result = model.invoke(
        "你是容器资源规划助手。根据输入返回 JSON 格式的 CPU 和内存预算。"
        "建议必须覆盖实验运行峰值，但不能超过输入中的 maxBudget。"
        "不要为了省资源而低于 ruleBudget。"
        '输出示例：{"cpus": 2.0, "memory_mb": 2048, "reason": "依据"}。\n'
        f"输入：{context}"
    )
    return result.model_dump()
