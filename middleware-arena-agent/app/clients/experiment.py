"""Agent -> experiment-service internal HTTP client."""

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

    # TODO[需要和 Java 一起定]:
    # 1. get_task / get_result / get_version / get_version_diff / get_logs。
    # 2. 404/409/5xx 业务错误映射。
    # 3. create_version / submit_task 等写操作必须经过 Human-in-the-loop。


experiment_client = ExperimentClient()
