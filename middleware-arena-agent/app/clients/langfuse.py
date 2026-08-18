"""Langfuse 客户端封装。

Langfuse 属于外部可观测/评测服务，因此放在 ``app.clients``，而不是业务 Service。
Agent 的核心分析即使 Langfuse 挂了也应该继续执行，所以这里采用“可选依赖”设计：
没有 Key 或初始化失败时返回 ``None``，上层自动降级为无 tracing 模式。
"""

import logging
from functools import lru_cache

from langfuse import Langfuse

from app.core.config import get_settings

logger = logging.getLogger(__name__)


@lru_cache(maxsize=1)
def get_langfuse_client() -> Langfuse | None:
    """获取进程级 Langfuse Client。

    为什么使用 ``lru_cache(maxsize=1)``：
    Langfuse Client 不需要每个 Node、每次请求重新创建；整个进程共享一个实例即可。

    返回 ``None`` 的含义不是“分析失败”，而是“本次不记录 Langfuse Trace”。
    """
    settings = get_settings()

    # public/secret key 必须同时存在；本地没配置时直接关闭 Langfuse。
    if not settings.langfuse_public_key or not settings.langfuse_secret_key:
        return None

    try:
        return Langfuse(
            public_key=settings.langfuse_public_key,
            secret_key=settings.langfuse_secret_key,
            base_url=settings.langfuse_base_url,
            # environment 会作为 Langfuse 中的环境标签，区分开发/生产数据。
            environment=settings.langfuse_tracing_environment,
        )
    except Exception:
        # 可观测系统不能反过来拖垮主业务，因此只记日志，不继续向上抛异常。
        logger.exception("Langfuse initialization failed; continue without tracing")
        return None


# TODO[Agent Core - 由你实现]:
# 1. 给 LangGraph/LangChain 接 CallbackHandler / tracing。
# 2. Trace metadata 写入 analysisId / taskId / versionId / middlewareType。
# 3. 每次使用 Langfuse Prompt 时记录 prompt name + version，便于复现实验。
# 4. 服务关闭时按 SDK 要求 flush，避免最后一批 trace 还在缓冲区。
