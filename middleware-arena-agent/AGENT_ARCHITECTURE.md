# Middleware Arena Agent 架构骨架

## 1. 服务定位

1. `middleware-arena-agent` 是独立 Python 微服务，FastAPI 端口 9500。
2. 用户主动操作走 HTTP：Frontend -> Gateway -> Agent。
3. Runner 完成后的自动分析走 MQ：Experiment -> RabbitMQ -> Agent。
4. HTTP / MQ 最终都进入同一个 `analysis_service`，再执行 LangGraph，禁止维护两套分析逻辑。
5. Agent 获取代码/metrics/diff/logs 时走 Experiment Internal API，不把大字段塞进 MQ。

## 2. 对外 HTTP

```text
Frontend
   -> Gateway :8000
      -> /agent/**
         -> Agent :9500
```

第一版接口：

```text
POST /agent/analyze
POST /agent/patch
POST /agent/compare
```

Gateway 当前通过 `AGENT_SERVICE_URL` 直接转发；Agent 暂不注册 Nacos。

## 3. 自动分析 MQ

```text
Runner SUCCESS
   -> Experiment 保存 experiment_result
   -> 创建 experiment_analysis(CREATED)
   -> AgentAnalysisTaskProducer
   -> agent.analysis.exchange
      routing-key = agent.analysis
   -> agent.analysis.queue
   -> Python consumer
   -> analysis_service
   -> LangGraph
   -> agent.analysis.status.exchange
   -> agent.analysis.status.queue
   -> Experiment 持久化结果
```

### MQ 可靠性

1. exchange/queue 都 durable。
2. Python consumer 使用 manual ack + prefetch=1。
3. 单次消费最多尝试 3 次；耗尽后 `reject(requeue=False)`。
4. `agent.analysis.queue` 配置 DLX：`agent.analysis.dlx -> agent.analysis.dlq`。
5. Python 成功发布最终 status/result 后，才 ACK 原 analysis task。
6. Java/Python 对同名队列的 durable、DLX arguments 必须完全一致，否则 RabbitMQ 会 `PRECONDITION_FAILED`。

Java 契约：`ExperimentRabbitConfig/AgentRabbitConfig`。
Python 契约：`app/mq/constants.py`。

## 4. MQ 消息字段

自动分析任务只传轻量标识：

```text
analysisId          experiment_analysis.id，主要幂等键
taskId              experiment_task.id
userId              审计/配额
versionId           experiment_version.id
baselineTaskId      可选；优化前后/基线任务
middlewareType      redis/rabbitmq/seata/elasticsearch
analysisType        第一版 PERFORMANCE_DIAGNOSIS
triggerType         AUTO/MANUAL/RETRY
dispatchId          本次 MQ 投递唯一 ID
queuedAtEpochMs     排队时间/超时统计
```

明确不放 MQ：

```text
filesJson / 代码正文
metricsJson
运行日志
完整 code diff
大段 report
```

这些由 `load_context` 收到 ID 后通过内部 HTTP 拉取。

## 5. LangGraph 主流程

```text
load_context
  -> analyze_metrics
  -> middleware_router
  -> redis/rabbitmq/seata/elasticsearch subgraph
  -> analyze_code
  -> generate_hypothesis
  -> judge_bottleneck
  -> generate_patch
  -> human approval
  -> create new version / rerun
  -> compare result
  -> generate_report
```

## 6. 目录职责

```text
app/
├── api/                 # /agent/analyze、patch、compare
├── mq/                  # RabbitMQ constants/message/connection/consumer/publisher
├── graph/
│   ├── builder.py       # 主 LangGraph
│   ├── state.py         # 全局 AnalysisState
│   ├── router.py        # 条件路由
│   ├── nodes/           # 通用分析节点
│   └── subgraphs/       # Redis / RabbitMQ / Seata / Elasticsearch 专家图
├── prompts/             # Langfuse Prompt 获取与名称管理
├── tools/               # Experiment / Metrics / Logs 等 Agent Tool
├── services/            # analysis_service / LLM / Langfuse / 外部服务客户端
├── core/                # 配置、日志、常量
└── evaluation/          # Langfuse Dataset / Experiment / Evaluator
```

## 7. 数据持久化

`middleware-arena-experiment/sql/upgrade_agent_analysis.sql` 预留：

1. `experiment_analysis`：分析状态、瓶颈、confidence、evidence、hypotheses、suggestions、report、Langfuse trace。
2. `experiment_patch`：候选多文件 Patch、用户接受/拒绝状态、最终创建的新 versionId。

Experiment Service 继续作为实验/版本/分析结果的数据 owner；Agent 不直接连接 Experiment MySQL。

## 8. Langfuse

1. Trace：一次 analysisId 对应一条主 Trace。
2. Prompt Management：metrics/code/redis/mq/seata/es/hypothesis/judge/patch/report 分 Prompt 管理。
3. Dataset：Redis N+1、Big Key、MQ 堆积、Seata 锁冲突、ES 深分页等真实故障样本。
4. Experiment + Evaluator：评估 bottleneck accuracy、evidence quality、patch quality。
5. Langfuse 故障只能影响可观测与 Eval，不能阻断 Agent 主链路。

## 9. 推荐实现顺序

1. `core/config.py + mq/constants.py + mq/messages.py`。
2. `experiment_analysis` 表 + Java AgentAnalysisTaskProducer。
3. Python `mq/connection.py + consumer.py`，先做到消费消息打印 analysisId。
4. `experiment_tools.py + load_context.py`，打通内部 HTTP。
5. `analysis_service + graph/state.py + builder.py`，先跑 Redis 只读诊断。
6. Langfuse Trace + Prompt Management。
7. status/result MQ 回传并持久化。
8. Patch + Human-in-the-loop + V1/V2 compare。

## 10. 关键约束

1. `experiment_version + OSS` 是本次实验代码 source of truth，不以 GitHub HEAD 为准。
2. Agent 只生成 Patch；用户确认后由 experiment-service 创建新版本。
3. 所有瓶颈结论必须关联 evidence。
4. 自动优化循环限制 `max_iterations`，默认 3。
5. MQ 拓扑/JSON 字段属于跨语言契约，Java/Python 必须同步修改。

## TODO

- [ ] 将现有 `app/state.py` 迁移到 `app/graph/state.py` 后删除旧入口。
- [ ] 在 `main.py` 挂载 `/agent` API Router 和 FastAPI lifespan MQ consumer。
- [ ] 增加 aio-pika / httpx / pydantic-settings / langfuse 依赖并锁定版本。
- [ ] 实现 Java AgentAnalysisTaskProducer / AgentAnalysisStatusConsumer。
- [ ] 实现 Python consumer/publisher 与幂等控制。
- [ ] 为 Redis 场景准备第一批 Langfuse Dataset。
- [ ] 增加 Java/Python MQ 契约测试。
