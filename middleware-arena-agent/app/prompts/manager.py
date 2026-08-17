"""Langfuse Prompt 管理入口。

1. Node 通过本模块获取 Prompt，不直接写远端 Prompt API 调用。
2. 默认读取 production label 并编译变量。
3. Langfuse 不可用时使用调用方传入的本地 fallback，保证分析主链可降级。

TODO:
- [ ] fallback Prompt 文件按中间件分别落到 prompts/fallback/。
- [ ] 将返回的 version 统一写入 Langfuse trace metadata / experiment_analysis。
- [ ] 后续增加本地 TTL cache，减少高并发下 Prompt API 请求。
"""

import logging
from dataclasses import dataclass
from typing import Any

from app.clients.langfuse import get_langfuse_client

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class RenderedPrompt:
    content: Any
    version: int | None
    source: str


def get_prompt(
    name: str,
    variables: dict[str, Any] | None = None,
    *,
    label: str = "production",
    fallback: Any | None = None,
) -> RenderedPrompt:
    variables = variables or {}
    client = get_langfuse_client()

    if client is not None:
        try:
            prompt = client.get_prompt(name, label=label)
            return RenderedPrompt(
                content=prompt.compile(**variables),
                version=getattr(prompt, "version", None),
                source="langfuse",
            )
        except Exception:
            logger.exception("Langfuse prompt fetch failed: %s", name)

    if fallback is None:
        raise RuntimeError(f"Prompt {name!r} 不可用，且没有配置 fallback")

    if isinstance(fallback, str):
        try:
            fallback = fallback.format(**variables)
        except (KeyError, IndexError, ValueError):
            pass
    return RenderedPrompt(content=fallback, version=None, source="fallback")
