"""Langfuse 客户端封装。

Langfuse 属于外部可观测/评测服务，因此放在 ``app.clients``，而不是业务 Service。
Agent 的核心分析即使 Langfuse 挂了也应该继续执行，所以这里采用“可选依赖”设计：
没有 Key 或初始化失败时返回 ``None``，上层自动降级为无 tracing 模式。
"""

import logging
import os
from functools import lru_cache
from typing import Any
from urllib.parse import urlparse

import httpx
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
        _exclude_langfuse_from_system_proxy(settings.langfuse_base_url)
        return Langfuse(
            public_key=settings.langfuse_public_key,
            secret_key=settings.langfuse_secret_key,
            base_url=settings.langfuse_base_url,
            # 本机可能配置了仅供其他工具使用的代理。Langfuse 直接访问公网，
            # 不继承系统代理可避免失效代理让 Prompt 和 Trace 全部静默降级。
            httpx_client=httpx.Client(
                timeout=10,
                trust_env=False,
            ),
            # environment 会作为 Langfuse 中的环境标签，区分开发/生产数据。
            environment=settings.langfuse_tracing_environment,
        )
    except Exception:
        # 可观测系统不能反过来拖垮主业务，因此只记日志，不继续向上抛异常。
        logger.exception("Langfuse initialization failed; continue without tracing")
        return None


def _exclude_langfuse_from_system_proxy(base_url: str) -> None:
    """把 Langfuse 主机加入 NO_PROXY，覆盖 OTLP exporter 的代理继承。"""
    host = urlparse(base_url).hostname
    if not host:
        return

    for variable in ("NO_PROXY", "no_proxy"):
        entries = [
            item.strip()
            for item in os.environ.get(variable, "").split(",")
            if item.strip()
        ]
        if host not in entries:
            entries.append(host)
            os.environ[variable] = ",".join(entries)


def build_langfuse_run_config(
    *,
    run_name: str,
    metadata: dict[str, Any],
) -> dict[str, Any]:
    """构造 LangChain 调用配置，让 LLM Generation 进入 Langfuse Trace。"""
    config: dict[str, Any] = {
        "run_name": run_name,
        "metadata": metadata,
    }
    if get_langfuse_client() is None:
        return config

    try:
        from langfuse.langchain import CallbackHandler

        config["callbacks"] = [CallbackHandler()]
    except Exception:
        logger.exception("Langfuse CallbackHandler initialization failed")
    return config


def flush_langfuse() -> None:
    """刷新 Langfuse 缓冲区；失败不影响服务关闭。"""
    client = get_langfuse_client()
    if client is None:
        return
    try:
        client.flush()
    except Exception:
        logger.exception("Langfuse flush failed")


# TODO[Agent Core - 由你实现]:
# 1. 主图调用时补充统一的 analysisId/taskId Trace 根节点。
# 2. 将 Prompt 版本同步写入 experiment_analysis.prompt_versions_json，便于离线复现。
