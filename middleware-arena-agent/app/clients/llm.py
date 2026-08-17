"""OpenAI-compatible LLM 调用封装。

当前文件只服务于“资源建议”这个简单场景，后续 LangGraph 节点需要统一模型工厂时再扩展。

为什么放在 ``clients``：LLM 本质上也是一个外部服务，和调用 experiment-service 一样，
这里负责 API/SDK 接入；真正“什么时候调用、拿结果做什么”属于 services/graph。
"""

from typing import Any

from langchain_openai import ChatOpenAI
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


def request_resource_advice(context: dict[str, Any]) -> dict[str, Any]:
    """调用 LLM 生成资源预算建议。

    参数 ``context`` 中包含规则预算、历史预算、最大预算等信息。
    返回普通 dict 是为了让现有 resource_advice service 使用简单；后续也可以直接返回
    ``ResourceAdviceOutput``，让类型约束更强。

    这个函数失败时不自己 fallback，而是把异常抛给 service；
    service 决定使用规则预算兜底，这样 Client 不参与业务决策。
    """
    settings = get_settings()

    # 没配置 Key 就直接报错，避免 SDK 发出一个必然失败的网络请求。
    if not settings.openai_api_key:
        raise RuntimeError("未配置 OPENAI_API_KEY")

    # ChatOpenAI 不只支持 OpenAI 官方 API；只要服务兼容 OpenAI 协议，
    # 就可以通过 base_url + model 接 DeepSeek 等模型。
    model = ChatOpenAI(
        api_key=settings.openai_api_key,
        base_url=settings.openai_api_base,
        model=settings.openai_model,
        # 资源预算希望稳定可重复，因此这里关闭随机性。
        temperature=0,
    ).with_structured_output(
        ResourceAdviceOutput,
        # json_mode 要求模型输出 JSON，再由 Pydantic 做字段校验。
        method="json_mode",
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


# TODO[Agent Core - 由你实现]:
# 1. 增加 get_chat_model() / model factory，供所有 LangGraph Node 统一复用。
# 2. 根据 node 类型选择模型/temperature/reasoning effort。
# 3. LangGraph LLM 调用接 Langfuse callback，而不是每个函数手写 trace。
