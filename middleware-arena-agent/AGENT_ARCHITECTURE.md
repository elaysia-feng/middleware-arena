# Middleware Arena Agent 架构骨架

## 1. 服务定位

1. `middleware-arena-agent` 是独立 Python 微服务，FastAPI 端口 9500。
2. 用户主动操作走 HTTP：Frontend -> Gateway -> Agent。
3. Runner 完成后的自动分析走 MQ：Experiment -> RabbitMQ -> Agent。
4. HTTP / MQ 最终都进入同一个 `analysis_service`，再执行 LangGraph。
5. Agent 获取代码/metrics/diff/logs 时走 Experiment Internal API，不把大字段塞进 MQ。

## 2. 模型分层约定

不要把所有 Pydantic Model 都叫 DTO，也不要把模型定义塞进 Service。

```text
HTTP
AnalyzeRequest
     |
     v
AnalysisCommand <----- AgentAnalysisTaskMessage (MQ)
     |
     v
analysis_service
     |
     v
AnalysisState
     |
     v
LangGraph
     |
     v
AnalysisResult
   /       \
  v         v
AnalyzeResponse   AgentAnalysisStatusMessage
HTTP              MQ
                     |
                     v
              Experiment Service
                     |
                     v
              ExperimentAnalysis
                 Entity / PO
                     |
                     v
                   MySQL
```

统一命名：

```text
*Request / *Response   HTTP 边界
*Message               MQ 跨服务契约
*Command / *Result     Service 内部 DTO
*State                 LangGraph 运行时状态
ExperimentAnalysis     Java Entity / PO
ExperimentPatch        Java Entity / PO
```

Python Agent 不直接维护 Experiment MySQL Entity。

## 3. Python 目录

```text
app/
├── api/                         # FastAPI Controller / Router
│   ├── analyze.py
│   ├── patch.py
│   └── compare.py
│
├── schemas/
│   ├── api/
│   │   └── analysis.py          # Request / Response
│   ├── commands/
│   │   └── analysis.py          # AnalysisCommand
│   ├── results/
│   │   └── analysis.py          # AnalysisResult
│   └── mq/
│       └── analysis.py          # RabbitMQ Message
│
├── services/
│   ├── analysis_service.py      # Command -> Result
│   ├── experiment_client.py
│   ├── llm.py
│   └── langfuse.py
│
├── mq/                          # RabbitMQ 基础设施，不放 DTO
│   ├── constants.py
│   ├── connection.py
│   ├── consumer.py
│   └── publisher.py
│
├── graph/
│   ├── state.py                 # 唯一 AnalysisState
│   ├── builder.py
│   ├── router.py
│   ├── nodes/
│   └── subgraphs/
│       ├── redis/
│       ├── rabbitmq/
│       ├── seata/
│       └── elasticsearch/
│
├── prompts/                     # Langfuse Prompt 管理
├── tools/                       # Agent Tool
├── core/                        # Settings / 基础配置
└── evaluation/                  # Langfuse Eval
```

## 4. Java 持久化模型

数据库所有权属于 `experiment-service`。

```text
experiment.domain/
├── ExperimentAnalysis.java     # experiment_analysis PO / Entity
└── ExperimentPatch.java        # experiment_patch PO / Entity

experiment.mapper/
├── ExperimentAnalysisMapper.java
└── ExperimentPatchMapper.java
```

Python 通过 HTTP/MQ 与 Java 协作，不直接连接 Experiment MySQL。

## 5. 对外 HTTP

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

## 6. 自动分析 MQ

```text
Runner SUCCESS
   -> Experiment 保存 experiment_result
   -> 创建 experiment_analysis(CREATED)
   -> AgentAnalysisTaskProducer
   -> agent.analysis.exchange
      routing-key = agent.analysis
   -> agent.analysis.queue
   -> Python consumer
   -> AnalysisCommand
   -> analysis_service
   -> LangGraph
   -> AnalysisResult
   -> AgentAnalysisStatusMessage
   -> agent.analysis.status.exchange
   -> agent.analysis.status.queue
   -> Experiment 持久化结果
```

### MQ 可靠性

1. exchange/queue durable。
2. Python consumer manual ack + prefetch=1。
3. 单次消费最多尝试 3 次；耗尽后 `reject(requeue=False)`。
4. `agent.analysis.queue` 配置 DLX：`agent.analysis.dlx -> agent.analysis.dlq`。
5. Python 成功发布最终 status/result 后才 ACK 原 analysis task。
6. Java/Python 对同名队列的 durable、DLX arguments 必须完全一致。

## 7. MQ 消息字段

```text
analysisId
 taskId
 userId
 versionId
 baselineTaskId
 middlewareType
 analysisType
 triggerType
 dispatchId
 queuedAtEpochMs
```

明确不放 MQ：

```text
代码正文
metricsJson
运行日志
完整 code diff
大段 report
```

这些由 `load_context` 收到 ID 后通过内部 HTTP 拉取。

## 8. LangGraph 主流程

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

`AnalysisState` 只用于图执行过程，不直接拿 Request/Message 当 State。

## 9. 数据持久化

`middleware-arena-experiment/sql/upgrade_agent_analysis.sql`：

1. `experiment_analysis`：分析状态、瓶颈、confidence、evidence、hypotheses、suggestions、report、Langfuse trace。
2. `experiment_patch`：候选多文件 Patch、用户接受/拒绝状态、最终创建的新 versionId。

## 10. Langfuse

1. Trace：一次 analysisId 对应一条主 Trace。
2. Prompt Management：metrics/code/redis/mq/seata/es/hypothesis/judge/patch/report 分 Prompt 管理。
3. Dataset：Redis N+1、Big Key、MQ 堆积、Seata 锁冲突、ES 深分页等故障样本。
4. Experiment + Evaluator：评估 bottleneck accuracy、evidence quality、patch quality。
5. Langfuse 故障只能影响可观测与 Eval，不能阻断 Agent 主链路。

## 11. 你后续主要实现的核心逻辑

```text
analysis_service.run_analysis
        |
        v
load_context
        |
        v
analyze_metrics / analyze_code
        |
        v
middleware subgraph
        |
        v
generate_hypothesis
        |
        v
judge_bottleneck
        |
        v
generate_patch / generate_report
```

基础配置、HTTP DTO、MQ DTO、RabbitMQ 连接、Java PO/Mapper 不应再混进核心推理逻辑。

## TODO

- [ ] 实现 `analysis_service.run_analysis`。
- [ ] 实现 Experiment Internal API 的具体 task/result/version/diff 路径。
- [ ] 实现 Java AgentAnalysisStatusConsumer 的状态机和幂等落库。
- [ ] 为 Evidence/Hypothesis/Patch 增加结构化模型。
- [ ] 为 Redis 场景准备第一批 Langfuse Dataset。
- [ ] 增加 Java/Python MQ 契约测试。
