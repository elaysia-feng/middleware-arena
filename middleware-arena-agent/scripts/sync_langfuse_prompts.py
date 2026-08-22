r"""把 Agent 本地 Prompt 初始版本同步到 Langfuse。

脚本只创建远端尚不存在的 Prompt；已经存在时不会生成无意义的新版本。
运行方式：``.venv\Scripts\python.exe scripts\sync_langfuse_prompts.py``。
"""

import sys
from pathlib import Path

# 允许直接从 scripts 目录执行，同时仍复用项目内的 app 包。
PROJECT_ROOT = Path(__file__).resolve().parents[1]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from langfuse.api.commons.errors.not_found_error import NotFoundError

from app.clients.langfuse import get_langfuse_client
from app.graph.subgraphs.elasticsearch.prompt import (
    FALLBACK_PROMPT as ELASTICSEARCH_PROMPT,
    PROMPT_NAME as ELASTICSEARCH_PROMPT_NAME,
)
from app.graph.subgraphs.generic.prompt import (
    FALLBACK_PROMPT as GENERIC_PROMPT,
    PROMPT_NAME as GENERIC_PROMPT_NAME,
)
from app.graph.subgraphs.rabbitmq.prompt import (
    FALLBACK_PROMPT as RABBITMQ_PROMPT,
    PROMPT_NAME as RABBITMQ_PROMPT_NAME,
)
from app.graph.subgraphs.redis.prompt import (
    FALLBACK_PROMPT as REDIS_PROMPT,
    PROMPT_NAME as REDIS_PROMPT_NAME,
)
from app.graph.subgraphs.seata.prompt import (
    FALLBACK_PROMPT as SEATA_PROMPT,
    PROMPT_NAME as SEATA_PROMPT_NAME,
)
from app.prompts.fallback.bottleneck import (
    FALLBACK_PROMPT as BOTTLENECK_PROMPT,
    PROMPT_NAME as BOTTLENECK_PROMPT_NAME,
)
from app.prompts.fallback.hypothesis import (
    FALLBACK_PROMPT as HYPOTHESIS_PROMPT,
    PROMPT_NAME as HYPOTHESIS_PROMPT_NAME,
)
from app.prompts.fallback.patch import (
    FALLBACK_PROMPT as PATCH_PROMPT,
    PROMPT_NAME as PATCH_PROMPT_NAME,
)
from app.prompts.fallback.report import (
    FALLBACK_PROMPT as REPORT_PROMPT,
    PROMPT_NAME as REPORT_PROMPT_NAME,
)


PROMPTS = {
    REDIS_PROMPT_NAME: REDIS_PROMPT,
    RABBITMQ_PROMPT_NAME: RABBITMQ_PROMPT,
    SEATA_PROMPT_NAME: SEATA_PROMPT,
    ELASTICSEARCH_PROMPT_NAME: ELASTICSEARCH_PROMPT,
    GENERIC_PROMPT_NAME: GENERIC_PROMPT,
    HYPOTHESIS_PROMPT_NAME: HYPOTHESIS_PROMPT,
    BOTTLENECK_PROMPT_NAME: BOTTLENECK_PROMPT,
    PATCH_PROMPT_NAME: PATCH_PROMPT,
    REPORT_PROMPT_NAME: REPORT_PROMPT,
}


def main() -> None:
    """创建缺失 Prompt，并输出不含密钥的同步摘要。"""
    client = get_langfuse_client()
    if client is None:
        raise RuntimeError("Langfuse 未配置，无法同步 Prompt")

    created: list[str] = []
    skipped: list[str] = []
    for name, content in PROMPTS.items():
        try:
            client.get_prompt(
                name,
                label="production",
                max_retries=0,
                fetch_timeout_seconds=10,
            )
            skipped.append(name)
        except NotFoundError:
            client.create_prompt(
                name=name,
                prompt=content,
                labels=["production"],
                tags=["middleware-arena", "agent"],
                commit_message="初始化 Agent 本地 Prompt",
            )
            created.append(name)

    client.flush()
    print(f"created={len(created)}")
    print(f"skipped={len(skipped)}")
    for name in created:
        print(f"created_prompt={name}")


if __name__ == "__main__":
    main()
