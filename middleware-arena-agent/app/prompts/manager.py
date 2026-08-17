"""Langfuse Prompt 统一管理入口。

为什么不让每个 LangGraph Node 自己 ``client.get_prompt(...)``：
1. Prompt 获取、fallback、版本记录属于公共逻辑，散落后很难统一维护。
2. Langfuse 临时不可用时，所有 Node 都应该用同一种降级策略。
3. 后续做 TTL Cache / Prompt Version Trace 时只改这一处。

调用示例：

    prompt = get_prompt(
        "middleware-redis-diagnosis",
        variables={"metrics": metrics},
        fallback="本地兜底 Prompt...",
    )

    prompt.content   # 真正给 LLM 的 Prompt
    prompt.version   # Langfuse Prompt 版本，可写入 Trace/DB
    prompt.source    # langfuse 或 fallback
"""

import logging
from dataclasses import dataclass
from typing import Any

from app.clients.langfuse import get_langfuse_client

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class RenderedPrompt:
    """已经完成变量渲染、可直接交给 LLM 的 Prompt。

    这里使用 ``dataclass`` 而不是 Pydantic ``BaseModel``，因为它只是 Python 进程内部
    的轻量值对象，不需要 HTTP/MQ 校验或 OpenAPI Schema。

    ``frozen=True`` 表示创建后不允许修改字段，避免 Node 意外改掉 Prompt 版本信息。
    """

    # 编译后的 Prompt 内容；Langfuse Chat Prompt 时也可能不是单纯 str，所以用 Any。
    content: Any
    # Langfuse 中的 Prompt Version；fallback 本地 Prompt 没有远端版本，因此为 None。
    version: int | None
    # "langfuse" 或 "fallback"，方便 Trace/日志知道本次实际用了哪一路。
    source: str


def get_prompt(
    name: str,
    variables: dict[str, Any] | None = None,
    *,
    label: str = "production",
    fallback: Any | None = None,
) -> RenderedPrompt:
    """优先从 Langfuse 获取 Prompt，失败时回退到本地 fallback。

    参数：
    - ``name``：Langfuse Prompt 名称。
    - ``variables``：Prompt 模板变量，例如 metrics/code_diff。
    - ``label``：Langfuse Label，默认 production，不要求调用方写死版本号。
    - ``fallback``：远端不可用时的本地 Prompt；核心链路建议必须提供。

    返回：
    ``RenderedPrompt``，同时携带内容、版本和来源。
    """

    # None 转为空 dict，后面可以直接 **variables。
    variables = variables or {}
    client = get_langfuse_client()

    # ------------------------------------------------------------------
    # 1. 优先使用 Langfuse Prompt Management
    # ------------------------------------------------------------------
    if client is not None:
        try:
            # label=production 让线上 Prompt 可以通过 Langfuse UI 切版本，代码无需重新部署。
            prompt = client.get_prompt(name, label=label)

            return RenderedPrompt(
                # compile 会把模板中的变量替换为本次实际值。
                content=prompt.compile(**variables),
                # 使用 getattr 是为了在不同 Prompt 类型/SDK 版本下更稳健。
                version=getattr(prompt, "version", None),
                source="langfuse",
            )
        except Exception:
            # Prompt 平台故障不能直接让整个 Agent 分析链路挂掉，继续尝试 fallback。
            logger.exception("Langfuse prompt fetch failed: %s", name)

    # ------------------------------------------------------------------
    # 2. Langfuse 不可用 -> 本地 fallback
    # ------------------------------------------------------------------
    if fallback is None:
        # 没有远端 Prompt、又没有本地兜底时已经无法继续安全调用 LLM，所以明确失败。
        raise RuntimeError(f"Prompt {name!r} 不可用，且没有配置 fallback")

    if isinstance(fallback, str):
        try:
            # 简单字符串模板允许使用 {metrics} / {code} 等 Python format 变量。
            fallback = fallback.format(**variables)
        except (KeyError, IndexError, ValueError):
            # Prompt 本身可能包含 JSON 示例：{"status": "good"}，其中花括号并不是模板变量。
            # 为避免 format 把 JSON 花括号误判，这里保留原始 fallback，由 Node 自己处理复杂模板。
            pass

    return RenderedPrompt(
        content=fallback,
        version=None,
        source="fallback",
    )


# TODO:
# 1. fallback Prompt 按中间件放到 prompts/fallback/，不要长期写在 Node 函数里。
# 2. 每次返回 version 后，把 prompt name/version 写入 Langfuse Trace metadata。
# 3. 同时把关键 Prompt Version 写入 experiment_analysis.prompt_versions_json，便于复现。
# 4. 增加短 TTL Cache，避免高并发时每个 Node 都请求一次 Langfuse Prompt API。
