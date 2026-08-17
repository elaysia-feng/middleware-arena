"""Langfuse client integration.

Langfuse is an external service, so the client lives under app.clients rather than
inside business services.
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


# TODO[Agent Core]:
# 1. 接 LangGraph/LangChain callback handler。
# 2. trace metadata 写 analysisId/taskId/versionId/prompt version。
# 3. 服务关闭时按需要 flush。
