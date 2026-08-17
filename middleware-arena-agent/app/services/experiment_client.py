"""Agent -> experiment-service 内部 HTTP 客户端。

1. 统一 baseUrl / internal token / timeout，Tool 不直接拼 httpx 请求。
2. 提供通用 GET/POST JSON 能力；具体 internal endpoint 路径由 Java 端接口定稿后补。
3. 非 2xx 统一 raise_for_status，交上层决定重试还是失败。

TODO[需要和 Java 一起定]:
- [ ] 确定 getTask/getResult/getVersion/getVersionDiff/getLogs 的 internal URL。
- [ ] 对 404/409/5xx 做业务错误映射。
- [ ] 写操作（createVersion/submitTask）必须经过 Human-in-the-loop 授权。
"""

from typing import Any

import httpx

from app.core.config import Settings, get_settings


class ExperimentClient:
    def __init__(self, settings: Settings | None = None) -> None:
        self.settings = settings or get_settings()

    def _headers(self) -> dict[str, str]:
        return {
            self.settings.internal_token_header: self.settings.ma_internal_token,
            "Accept": "application/json",
        }

    async def get_json(self, path: str, params: dict[str, Any] | None = None) -> Any:
        async with httpx.AsyncClient(
            base_url=self.settings.experiment_service_url,
            timeout=self.settings.internal_http_timeout_seconds,
            headers=self._headers(),
        ) as client:
            response = await client.get(path, params=params)
            response.raise_for_status()
            return response.json()

    async def post_json(self, path: str, payload: dict[str, Any]) -> Any:
        async with httpx.AsyncClient(
            base_url=self.settings.experiment_service_url,
            timeout=self.settings.internal_http_timeout_seconds,
            headers=self._headers(),
        ) as client:
            response = await client.post(path, json=payload)
            response.raise_for_status()
            return response.json()


experiment_client = ExperimentClient()
