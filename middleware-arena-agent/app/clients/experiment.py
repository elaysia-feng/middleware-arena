"""Agent -> experiment-service 的内部 HTTP Client。

为什么单独放在 ``app/clients``：
- ``services`` 负责业务流程；
- ``clients`` 只负责“怎么调用外部服务”。

这样 LangGraph Tool 或 Service 不需要知道 httpx、Header、base_url、timeout 等细节，
只依赖 ExperimentClient 提供的方法。

重要约束：Agent 不直接查询 Experiment MySQL。实验任务、版本、指标等数据的 owner
仍然是 Java experiment-service，Python 通过内部 HTTP API 获取。
"""

from typing import Any

import httpx

from app.core.config import Settings, get_settings


class ExperimentClient:
    """封装对 experiment-service 的 HTTP 调用。

    ``settings`` 支持依赖注入：
    - 生产代码不传，默认使用 get_settings()；
    - 单元测试可以传一个测试 Settings，改成 mock server 地址。
    """

    def __init__(self, settings: Settings | None = None) -> None:
        self.settings = settings or get_settings()

    def _headers(self) -> dict[str, str]:
        """构造所有内部请求共用的 Header。

        ``X-Internal-Token`` 不是用户 JWT，而是 Java/Python 服务之间的内部身份凭证。
        后续如果换成 mTLS/Nacos 服务鉴权，只需要改 Client 层，不影响 Graph 节点。
        """
        return {
            self.settings.internal_token_header: self.settings.ma_internal_token,
            "Accept": "application/json",
        }

    async def get_json(
        self,
        path: str,
        params: dict[str, Any] | None = None,
    ) -> Any:
        """发送 GET 请求并把响应解析成 JSON。

        参数：
        - ``path``：相对于 experiment_service_url 的路径，例如 /internal/tasks/1。
        - ``params``：URL Query 参数。

        ``raise_for_status()`` 会把 4xx/5xx 转成异常，上层再判断是否应该重试。
        """
        async with httpx.AsyncClient(
            base_url=self.settings.experiment_service_url,
            timeout=self.settings.internal_http_timeout_seconds,
            headers=self._headers(),
        ) as client:
            response = await client.get(path, params=params)
            response.raise_for_status()
            return response.json()

    async def post_json(self, path: str, payload: dict[str, Any]) -> Any:
        """发送 JSON POST 请求并返回解析后的 JSON。

        当前先提供通用方法。后续应该逐步增加 get_task()/get_result() 等语义明确的方法，
        让 Graph Tool 不直接拼 URL 字符串。
        """
        async with httpx.AsyncClient(
            base_url=self.settings.experiment_service_url,
            timeout=self.settings.internal_http_timeout_seconds,
            headers=self._headers(),
        ) as client:
            response = await client.post(path, json=payload)
            response.raise_for_status()
            return response.json()

    async def get_analysis_context(
        self,
        task_id: int,
        baseline_task_id: int | None = None,
    ) -> dict[str, Any]:
        """读取 load_context 节点所需的完整实验上下文。"""
        params = {"baselineTaskId": baseline_task_id} if baseline_task_id else None
        response = await self.get_json(
            f"/experiment/internal/agent/context/{task_id}",
            params=params,
        )
        if not isinstance(response, dict) or response.get("code") != 200:
            message = response.get("message") if isinstance(response, dict) else None
            raise RuntimeError(message or "experiment-service 返回格式错误")
        context = response.get("data")
        if not isinstance(context, dict):
            raise RuntimeError("experiment-service 未返回分析上下文")
        return context

    async def find_similar_experiments(self, task_id: int, limit: int = 5) -> list[dict[str, Any]]:
        """读取同中间件、同场景且指标接近的历史成功实验。"""
        response = await self.get_json(
            f"/experiment/internal/agent/similar/{task_id}",
            params={"limit": limit},
        )
        if not isinstance(response, dict) or response.get("code") != 200:
            message = response.get("message") if isinstance(response, dict) else None
            raise RuntimeError(message or "experiment-service 返回格式错误")
        matches = response.get("data")
        if not isinstance(matches, list):
            raise RuntimeError("experiment-service 未返回相似实验列表")
        return [item for item in matches if isinstance(item, dict)]

    # TODO[需要你和 Java internal API 一起定]:
    # 1. get_task(task_id)：获取实验任务基本信息。
    # 2. get_result(task_id)：获取 QPS/P95/Error/CPU/Memory + metricsJson。
    # 3. get_version(version_id)：获取本次实验精确代码版本信息。
    # 4. get_version_diff(from_id, to_id)：获取代码差异。
    # 5. get_logs(task_id)：获取 Runner 执行日志。
    # 6. 对 404/409/5xx 映射成更明确的业务异常。
    # 7. create_version / submit_task 等写操作必须经过 Human-in-the-loop 授权。


# 进程内共享一个无状态 Client 包装对象；真正的 httpx client 目前仍按请求创建。
experiment_client = ExperimentClient()

# TODO[性能优化]:
# 如果内部 HTTP 调用频繁，可以把 httpx.AsyncClient 也提升为 lifespan 级长连接，
# 复用 HTTP connection pool，避免每次请求都重新建立 TCP 连接。
