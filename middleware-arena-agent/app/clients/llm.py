"""MiniMax LLM 调用封装。

当前文件只服务于“资源建议”这个简单场景，后续 LangGraph 节点需要统一模型工厂时再扩展。

为什么放在 ``clients``：LLM 本质上也是一个外部服务，和调用 experiment-service 一样，
这里负责 API/SDK 接入；真正“什么时候调用、拿结果做什么”属于 services/graph。
"""

import json
from pathlib import Path
from typing import Any

from langchain_anthropic import ChatAnthropic
from pydantic import BaseModel, Field

from app.core.config import get_settings


class ResourceAdviceOutput(BaseModel):
    """要求 LLM 严格返回的结构化结果。

    如果直接让模型返回自然语言，例如“建议 2 核 2G”，代码还要自己解析字符串。
    ``with_structured_output`` 配合 Pydantic 可以直接得到有类型、有校验的对象。
    """

    cpus: float = Field(
        gt=0,
        description="LLM 建议的 CPU 核数，必须大于 0",
    )
    memory_mb: int = Field(
        gt=0,
        description="LLM 建议的内存大小，单位 MB",
    )
    reason: str = Field(
        min_length=1,
        max_length=500,
        description="模型给出该资源预算的简短依据",
    )


def get_chat_model() -> ChatAnthropic:
    """创建供 LangGraph 节点使用的 MiniMax 对话模型。

    配置优先级为项目环境变量、``.env.local``、Claude Code 设置。最后一级仅用于
    本机开发，容器和生产环境仍应通过密钥管理系统注入 ``ANTHROPIC_API_KEY``。
    """
    settings = get_settings()
    claude_env = _read_claude_env()
    api_key = (
        settings.anthropic_api_key
        or claude_env.get("ANTHROPIC_AUTH_TOKEN")
    )
    if not api_key:
        raise RuntimeError("未配置 MiniMax API Key")

    base_url = settings.anthropic_base_url
    model_name = settings.anthropic_model
    if not settings.anthropic_api_key:
        base_url = claude_env.get("ANTHROPIC_BASE_URL") or base_url
        model_name = claude_env.get("ANTHROPIC_MODEL") or model_name

    return ChatAnthropic(
        api_key=api_key,
        base_url=base_url,
        model=model_name,
        temperature=0,
        max_tokens=4096,
    )


def _read_claude_env() -> dict[str, str]:
    """读取本机 Claude Code 环境配置，不记录或返回到业务结果中。"""
    settings_path = Path.home() / ".claude" / "settings.json"
    if not settings_path.is_file():
        return {}

    try:
        payload = json.loads(settings_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {}

    raw_env = payload.get("env")
    if not isinstance(raw_env, dict):
        return {}
    return {
        str(name): str(value)
        for name, value in raw_env.items()
        if value is not None
    }


def request_resource_advice(context: dict[str, Any]) -> dict[str, Any]:
    """调用 LLM 生成资源预算建议。

    参数 ``context`` 中包含规则预算、历史预算、最大预算等信息。
    返回普通 dict 是为了让现有 resource_advice service 使用简单；后续也可以直接返回
    ``ResourceAdviceOutput``，让类型约束更强。

    这个函数失败时不自己 fallback，而是把异常抛给 service；
    service 决定使用规则预算兜底，这样 Client 不参与业务决策。
    """
    model = get_chat_model().with_structured_output(
        ResourceAdviceOutput,
        # Anthropic 工具调用把输出约束到 Pydantic Schema。
        method="function_calling",
    )

    # 当前资源建议逻辑比较简单，所以先直接拼 Prompt。
    # Agent 核心节点的 Prompt 后续统一交给 Langfuse Prompt Management 管理。
    result = model.invoke(
        "你是容器资源规划助手。根据输入返回 JSON 格式的 CPU 和内存预算。"
        "建议必须覆盖实验运行峰值，但不能超过输入中的 maxBudget。"
        "不要为了省资源而低于 ruleBudget。"
        '输出示例：{"cpus": 2.0, "memory_mb": 2048, "reason": "依据"}。\n'
        f"输入：{context}"
    )

    # result 已经是 ResourceAdviceOutput，不是原始字符串。
    return result.model_dump()


# TODO[Agent Core]: 后续按节点成本和复杂度选择 MiniMax 模型档位。
