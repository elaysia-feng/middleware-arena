"""Langfuse 客户端封装。

1. 只有配置了 public/secret key 才启用，未配置时返回 None。
2. Langfuse 故障不能阻断核心 Agent 分析，所以初始化失败只记录日志。
3. Node/Prompt Manager 统一从这里拿 client，不要各自 new Langfuse。

TODO[核心接入]:
- [ ] LangGraph 编译后接 Langfuse LangChain callback handler。
- [ ] 将 taskId/versionId/analysisId/promptVersion 写入 trace metadata。
- [ ] 在服务关闭时按需要 flush。
"""

import logging
from functools import lru_cache

from langfuse import Langfuse

from app.core.config import get_settings

logger = logging.getLogger(__name__)


@lru_cache(maxsize=1)
def get_langfuse_client() -> Langfuse | None:
    settings = get_settings()
    if not settings.langfuse_public_key or not settings.langfuse_secret_key:
        return None

    try:
        return Langfuse(
            public_key=settings.langfuse_public_key,
            secret_key=settings.langfuse_secret_key,
            base_url=settings.langfuse_base_url,
            environment=settings.langfuse_tracing_environment,
        )
    except Exception:
        logger.exception("Langfuse initialization failed; continue without tracing")
        return None
