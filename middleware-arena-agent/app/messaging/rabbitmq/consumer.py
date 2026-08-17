"""RabbitMQ 自动分析任务 Consumer。

这个文件的职责是“可靠地消费消息”，不是做性能诊断本身。

完整流程：
1. 从 ``agent.analysis.queue`` 收到 Java 发来的 JSON。
2. Pydantic 校验成 ``AgentAnalysisTaskMessage``。
3. 转换成协议无关的 ``AnalysisCommand``。
4. 调用统一 ``run_analysis``。
5. 成功后先发布 SUCCESS，再 ACK 原任务。
6. 失败超过最大次数后发布 FAILED，并 reject(requeue=False) 送入 DLQ。

为什么使用 manual ACK：
如果一收到消息 RabbitMQ 就自动 ACK，而 Agent 分析到一半进程崩了，这条任务会永久丢失。
manual ACK 可以等真正处理成功后再确认消息完成。
"""

import asyncio
import logging
import time

from aio_pika.abc import AbstractIncomingMessage
from pydantic import ValidationError

from app.core.config import Settings, get_settings
from app.messaging.rabbitmq.connection import RabbitMQManager, rabbitmq_manager
from app.messaging.rabbitmq.publisher import publish_status
from app.schemas.analysis import AnalysisCommand
from app.schemas.messages import AgentAnalysisStatusMessage, AgentAnalysisTaskMessage
from app.services.analysis import run_analysis

logger = logging.getLogger(__name__)


class AgentAnalysisConsumer:
    """消费自动分析任务，并把结果状态回传给 experiment-service。"""

    def __init__(
        self,
        manager: RabbitMQManager = rabbitmq_manager,
        settings: Settings | None = None,
    ) -> None:
        # Manager 负责连接/Queue；Consumer 只负责如何处理一条消息。
        self.manager = manager
        self.settings = settings or get_settings()

        # aio-pika consume() 会返回 consumer_tag，stop 时用它取消消费者注册。
        self.consumer_tag: str | None = None

    async def start(self) -> None:
        """在 analysis queue 上注册异步消费者。"""

        # 正常情况下 FastAPI lifespan 已经先 connect；
        # 这里再次检查，使 Consumer 单独测试/调用时也能工作。
        if self.manager.analysis_queue is None:
            await self.manager.connect()
        if self.manager.analysis_queue is None:
            raise RuntimeError("Agent analysis queue 未初始化")

        # no_ack=False = manual ACK。
        # RabbitMQ 会一直认为消息“处理中”，直到代码显式 ack/reject。
        self.consumer_tag = await self.manager.analysis_queue.consume(
            self._on_message,
            no_ack=False,
        )
        logger.info("Agent MQ consumer started")

    async def stop(self) -> None:
        """取消 Consumer 注册，不再接收新的分析任务。"""
        if self.consumer_tag and self.manager.analysis_queue is not None:
            await self.manager.analysis_queue.cancel(self.consumer_tag)
        self.consumer_tag = None

    async def _on_message(self, incoming: AbstractIncomingMessage) -> None:
        """处理 RabbitMQ 推送过来的一条原始消息。

        ``incoming.body`` 是 bytes，不能直接当可信 dict 使用；
        第一件事必须先通过 Pydantic 做 JSON + 字段类型校验。
        """

        # ------------------------------------------------------------------
        # 1. 校验 Java -> Python MQ 契约
        # ------------------------------------------------------------------
        try:
            task = AgentAnalysisTaskMessage.model_validate_json(incoming.body)
        except ValidationError:
            # 字段缺失/类型错误属于不可重试错误。
            # 重新排队也不会凭空变正确，所以直接 reject 到 DLQ。
            logger.exception("Invalid AgentAnalysisTaskMessage, reject to DLQ")
            await incoming.reject(requeue=False)
            return

        # ------------------------------------------------------------------
        # 2. MQ Message -> Service Command
        # ------------------------------------------------------------------
        # 从这里开始，后面的业务层不再依赖 RabbitMQ Message 类型。
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
            # ------------------------------------------------------------------
            # 3. 先告诉 Java：任务已经真正开始分析
            # ------------------------------------------------------------------
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

            # ------------------------------------------------------------------
            # 4. 执行 Agent 核心分析 + 本地有限重试
            # ------------------------------------------------------------------
            result = None
            for attempt in range(1, self.settings.agent_mq_max_attempts + 1):
                try:
                    result = await run_analysis(command)
                    break
                except Exception:
                    if attempt >= self.settings.agent_mq_max_attempts:
                        # 最后一次仍失败，交给外层统一发布 FAILED + reject。
                        raise

                    logger.exception(
                        "Agent analysis failed, retrying: analysisId=%s attempt=%s",
                        task.analysis_id,
                        attempt,
                    )

                    # 避免瞬时错误时无间隔疯狂重试 LLM/HTTP 服务。
                    await asyncio.sleep(
                        self.settings.agent_mq_retry_interval_seconds
                    )

            # 正常情况下成功一定会返回 AnalysisResult；这个检查防止未来逻辑错误返回 None。
            if result is None:
                raise RuntimeError("Agent analysis returned no result")

            # ------------------------------------------------------------------
            # 5. 先可靠发布 SUCCESS，再 ACK 原分析任务
            # ------------------------------------------------------------------
            # 顺序非常重要：如果先 ACK，再发布 SUCCESS 时进程崩溃，
            # Java 会一直看不到最终结果，但 RabbitMQ 又认为原任务已经处理完。
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

            # 到这里才能确认原任务处理成功。
            await incoming.ack()

        except Exception as exc:
            # ------------------------------------------------------------------
            # 6. 重试耗尽：发布 FAILED，原消息进入 DLQ
            # ------------------------------------------------------------------
            logger.exception(
                "Agent analysis exhausted retries: analysisId=%s",
                task.analysis_id,
            )

            try:
                await publish_status(
                    AgentAnalysisStatusMessage(
                        analysis_id=task.analysis_id,
                        task_id=task.task_id,
                        status="FAILED",
                        current_stage="FAILED",
                        error_code=type(exc).__name__,
                        # 限制长度，避免把巨大异常/敏感堆栈塞进 MQ。
                        error_message=str(exc)[:1000],
                        finished_at_epoch_ms=int(time.time() * 1000),
                    ),
                    self.manager,
                )
            except Exception:
                # FAILED 状态发布失败也不能掩盖原始异常，先记录日志。
                logger.exception(
                    "Failed to publish Agent FAILED status: analysisId=%s",
                    task.analysis_id,
                )

            # requeue=False：不回原队列无限循环。
            # 因 analysis queue 配了 x-dead-letter-exchange，所以会自动进入 DLQ。
            await incoming.reject(requeue=False)


# FastAPI 进程共享一个 Consumer，由 lifespan 负责 start()/stop()。
agent_analysis_consumer = AgentAnalysisConsumer()


# TODO[业务 - 由你实现]:
# 1. 执行 LLM 前按 analysisId/dispatchId 做幂等，避免重复投递重复计费。
# 2. 区分“可重试异常”和“不可重试异常”：
#    - timeout / 5xx / 临时限流：可重试；
#    - 参数错误 / 数据不存在：直接失败，不要重复调用 LLM。
# 3. 后续把简单 sleep 升级成指数退避 + jitter。
