"""RabbitMQ 自动分析任务 Consumer（租约版：快速 ack + 并发池）。

设计（解决"分析耗时几分钟，消息被握住几分钟"的问题）：

1. 从 ``agent.analysis.queue`` 收到 Java 发来的 JSON。
2. Pydantic 校验成 ``AgentAnalysisTaskMessage``。
3. 向 Java 抢分析租约（POST /experiment/internal/agent/claim/{analysisId}，
   Java 侧 QUEUED → ANALYZING 条件更新）。
4. 抢到 → **立刻 ACK 原消息**（MQ 不再被几分钟的分析握住），任务转交
   内部并发池（asyncio.Semaphore 限并发）异步执行；
   没抢到（重复投递 / 状态不允许）→ 也 ACK 丢弃，日志说明。
5. 后台执行 run_analysis：过程中 publish ANALYZING（同时起到续约作用，
   Java 消费者每次落库都会刷新 experiment_analysis.updatedAt）；
   成功 publish SUCCESS；本地重试耗尽 publish FAILED。
6. 崩溃兜底：ack 之后进程崩溃，MQ 侧消息已不在；但 Java 侧租约定时任务
   （AgentAnalysisLeaseService.reclaimStaleAnalyses）会发现 ANALYZING 超时未续约，
   自动重新投递任务消息，分析不丢。

为什么不再"分析全程握着消息"：RabbitMQ 有 consumer ack 超时上限（默认 30 分钟），
超时会强制断开消费者通道导致消息无限重投；租约方案把 ack 延迟压到毫秒级，
长耗时的可靠性改由 Java 租约回收负责。
"""

import asyncio
import logging
import time

from aio_pika.abc import AbstractIncomingMessage
from pydantic import ValidationError

from app.clients.experiment import ExperimentClient
from app.core.config import Settings, get_settings
from app.messaging.rabbitmq.connection import RabbitMQManager, rabbitmq_manager
from app.messaging.rabbitmq.publisher import publish_status
from app.schemas.analysis import AnalysisCommand
from app.schemas.messages import AgentAnalysisStatusMessage, AgentAnalysisTaskMessage
from app.services.analysis import run_analysis

logger = logging.getLogger(__name__)


class AgentAnalysisConsumer:
    """消费自动分析任务：抢租约 → 快速 ack → 并发池执行 → 回传状态。"""

    def __init__(
        self,
        manager: RabbitMQManager = rabbitmq_manager,
        settings: Settings | None = None,
        experiment_client: ExperimentClient | None = None,
    ) -> None:
        self.manager = manager
        self.settings = settings or get_settings()
        self.experiment_client = experiment_client or ExperimentClient(self.settings)
        # 并发池：限制同时运行的分析数量（LLM 并发费用 / 上下文拉取压力）
        self._semaphore = asyncio.Semaphore(self.settings.agent_max_concurrency)
        self._background_tasks: set[asyncio.Task] = set()
        self.consumer_tag: str | None = None

    async def start(self) -> None:
        """在 analysis queue 上注册异步消费者。"""

        # 正常情况下 FastAPI lifespan 已经先 connect；
        # 这里再次检查，使 Consumer 单独测试/调用时也能工作。
        if self.manager.analysis_queue is None:
            await self.manager.connect()
        if self.manager.analysis_queue is None:
            raise RuntimeError("Agent analysis queue 未初始化")

        # no_ack=False = manual ACK；ack 时机在租约抢占成功后立刻发生（毫秒级）。
        self.consumer_tag = await self.manager.analysis_queue.consume(
            self._on_message,
            no_ack=False,
        )
        logger.info("Agent MQ consumer started")

    async def stop(self) -> None:
        """取消 Consumer 注册，并等待在跑的分析任务收尾。"""
        if self.consumer_tag and self.manager.analysis_queue is not None:
            await self.manager.analysis_queue.cancel(self.consumer_tag)
        self.consumer_tag = None
        if self._background_tasks:
            await asyncio.gather(*self._background_tasks, return_exceptions=True)

    async def _on_message(self, incoming: AbstractIncomingMessage) -> None:
        """处理 RabbitMQ 推送过来的一条原始消息：校验 → 抢租约 → ack → 后台执行。"""

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
        # 2. 向 Java 抢租约（QUEUED → ANALYZING 条件更新）
        # ------------------------------------------------------------------
        try:
            claimed = await self.experiment_client.claim_analysis(
                task.analysis_id, task.task_id, task.dispatch_id,
            )
        except Exception:
            # 租约接口不可达：消息未 ack，Broker 稍后重投（任务不丢）。
            logger.exception(
                "claim analysis lease failed, requeue: analysisId=%s",
                task.analysis_id,
            )
            await incoming.nack(requeue=True)
            return

        if not claimed:
            # 没抢到：重复投递 / 已终态 / 状态不允许。ack 丢弃，日志说明即可。
            logger.info(
                "lease not claimed (duplicate or not dispatchable), drop message: analysisId=%s",
                task.analysis_id,
            )
            await incoming.ack()
            return

        # ------------------------------------------------------------------
        # 3. 抢到租约：立刻 ack（MQ 职责到此结束），任务交给并发池后台执行。
        #    崩溃兜底不在 MQ 层：Java 租约回收任务发现 ANALYZING 超时未续约会重派。
        # ------------------------------------------------------------------
        await incoming.ack()
        logger.info("lease claimed, analysis dispatched to pool: analysisId=%s", task.analysis_id)

        background = asyncio.create_task(self._execute(task))
        self._background_tasks.add(background)
        background.add_done_callback(self._background_tasks.discard)

    async def _execute(self, task: AgentAnalysisTaskMessage) -> None:
        """并发池内执行分析：ANALYZING → run_analysis（本地重试）→ SUCCESS / FAILED。"""
        # 1. 保留投递批次，所有状态回传都必须绑定当前租约。
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

        # 2. 有限并发执行并回传终态，由 Java 忽略失效批次的结果。
        async with self._semaphore:
            try:
                # 先告诉 Java：任务已经真正开始分析（同时刷新租约 updatedAt）
                await publish_status(
                    AgentAnalysisStatusMessage(
                        analysis_id=task.analysis_id,
                        task_id=task.task_id,
                        dispatch_id=task.dispatch_id,
                        status="ANALYZING",
                        current_stage="LOAD_CONTEXT",
                        progress=5,
                    ),
                    self.manager,
                )

                # 执行 Agent 核心分析 + 本地有限重试
                result = None
                for attempt in range(1, self.settings.agent_mq_max_attempts + 1):
                    try:
                        result = await run_analysis(command)
                        break
                    except Exception:
                        if attempt >= self.settings.agent_mq_max_attempts:
                            # 最后一次仍失败，交给外层统一发布 FAILED。
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

                # 正常情况下成功一定会返回 AnalysisResult；防止未来逻辑错误返回 None。
                if result is None:
                    raise RuntimeError("Agent analysis returned no result")

                # 成功：发布 SUCCESS（Java 消费者按 analysisId 幂等落库）
                await publish_status(
                    AgentAnalysisStatusMessage(
                        analysis_id=task.analysis_id,
                        task_id=task.task_id,
                        dispatch_id=task.dispatch_id,
                        status="SUCCESS",
                        current_stage="DONE",
                        progress=100,
                        result_json=result.model_dump_json(),
                        finished_at_epoch_ms=int(time.time() * 1000),
                    ),
                    self.manager,
                )
            except Exception as exc:
                # 重试耗尽：发布 FAILED（Java 消费者落错误信息；租约回收器见终态后不再干预）
                logger.exception(
                    "Agent analysis exhausted retries: analysisId=%s",
                    task.analysis_id,
                )

                try:
                    await publish_status(
                        AgentAnalysisStatusMessage(
                            analysis_id=task.analysis_id,
                            task_id=task.task_id,
                            dispatch_id=task.dispatch_id,
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


# FastAPI 进程共享一个 Consumer，由 lifespan 负责 start()/stop()。
agent_analysis_consumer = AgentAnalysisConsumer()
