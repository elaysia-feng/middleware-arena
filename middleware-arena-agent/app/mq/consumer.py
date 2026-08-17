"""Agent 自动分析任务消费者。

1. 消费 AgentAnalysisTaskMessage。
2. MQ Message 转换为 Service AnalysisCommand 后调用统一 run_analysis。
3. Service AnalysisResult 转换为 AgentAnalysisStatusMessage 回传。

TODO[业务]:
- [ ] 在执行 LLM 前增加 analysisId/dispatchId 幂等检查。
- [ ] 后续将 retry 分类为可重试异常/不可重试异常，参数错误不要重复调用 LLM。
"""

import asyncio
import logging
import time

from aio_pika.abc import AbstractIncomingMessage
from pydantic import ValidationError

from app.core.config import Settings, get_settings
from app.mq.connection import RabbitMQManager, rabbitmq_manager
from app.mq.publisher import publish_status
from app.schemas.mq.analysis_status_message import AgentAnalysisStatusMessage
from app.schemas.mq.analysis_task_message import AgentAnalysisTaskMessage
from app.schemas.service.analysis_command import AnalysisCommand
from app.services.analysis_service import run_analysis

logger = logging.getLogger(__name__)


class AgentAnalysisConsumer:
    def __init__(
        self,
        manager: RabbitMQManager = rabbitmq_manager,
        settings: Settings | None = None,
    ) -> None:
        self.manager = manager
        self.settings = settings or get_settings()
        self.consumer_tag: str | None = None

    async def start(self) -> None:
        if self.manager.analysis_queue is None:
            await self.manager.connect()
        if self.manager.analysis_queue is None:
            raise RuntimeError("Agent analysis queue 未初始化")
        self.consumer_tag = await self.manager.analysis_queue.consume(self._on_message, no_ack=False)
        logger.info("Agent MQ consumer started")

    async def stop(self) -> None:
        if self.consumer_tag and self.manager.analysis_queue is not None:
            await self.manager.analysis_queue.cancel(self.consumer_tag)
        self.consumer_tag = None

    async def _on_message(self, incoming: AbstractIncomingMessage) -> None:
        try:
            task = AgentAnalysisTaskMessage.model_validate_json(incoming.body)
        except ValidationError:
            logger.exception("Invalid AgentAnalysisTaskMessage, reject to DLQ")
            await incoming.reject(requeue=False)
            return

        command = AnalysisCommand(
            analysis_id=task.analysis_id,
            task_id=task.task_id,
            user_id=task.user_id,
            version_id=task.version_id,
            baseline_task_id=task.baseline_task_id,
            middleware_type=task.middleware_type,
            analysis_type=task.analysis_type,
            trigger_type=task.trigger_type,
            dispatch_id=task.dispatch_id,
        )

        try:
            await publish_status(
                AgentAnalysisStatusMessage(
                    analysis_id=task.analysis_id,
                    task_id=task.task_id,
                    status="ANALYZING",
                    current_stage="LOAD_CONTEXT",
                    progress=5,
                ),
                self.manager,
            )

            result = None
            for attempt in range(1, self.settings.agent_mq_max_attempts + 1):
                try:
                    result = await run_analysis(command)
                    break
                except Exception:
                    if attempt >= self.settings.agent_mq_max_attempts:
                        raise
                    logger.exception(
                        "Agent analysis failed, retrying: analysisId=%s attempt=%s",
                        task.analysis_id,
                        attempt,
                    )
                    await asyncio.sleep(self.settings.agent_mq_retry_interval_seconds)

            if result is None:
                raise RuntimeError("Agent analysis returned no result")

            await publish_status(
                AgentAnalysisStatusMessage(
                    analysis_id=task.analysis_id,
                    task_id=task.task_id,
                    status="SUCCESS",
                    current_stage="DONE",
                    progress=100,
                    result_json=result.model_dump_json(),
                    finished_at_epoch_ms=int(time.time() * 1000),
                ),
                self.manager,
            )
            await incoming.ack()
        except Exception as exc:
            logger.exception("Agent analysis exhausted retries: analysisId=%s", task.analysis_id)
            try:
                await publish_status(
                    AgentAnalysisStatusMessage(
                        analysis_id=task.analysis_id,
                        task_id=task.task_id,
                        status="FAILED",
                        current_stage="FAILED",
                        error_code=type(exc).__name__,
                        error_message=str(exc)[:1000],
                        finished_at_epoch_ms=int(time.time() * 1000),
                    ),
                    self.manager,
                )
            except Exception:
                logger.exception("Failed to publish Agent FAILED status: analysisId=%s", task.analysis_id)
            await incoming.reject(requeue=False)


agent_analysis_consumer = AgentAnalysisConsumer()
