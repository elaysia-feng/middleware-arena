# Middleware Arena Agent 架构骨架

## 1. 服务定位

1. `middleware-arena-agent` 是独立 Python / FastAPI 微服务，默认端口 9500。
2. 用户主动操作：Frontend -> Gateway -> Agent HTTP。
3. Runner 完成后的自动分析：Experiment -> RabbitMQ -> Agent。
4. HTTP / MQ 最终都进入同一个 `services.analysis.run_analysis()`，再执行 LangGraph。
5. Agent 通过 Experiment Internal API 获取代码、metrics、diff、logs，不把大字段塞进 MQ。

## 2. Python 项目结构约定

本项目按常见 FastAPI 服务结构组织，而不是照搬 Java 的 DTO/VO/PO 目录。

```text
middleware-arena-agent/
├── app/
│   ├── __init__.py
│   ├── main.py                         # FastAPI app + lifespan
│   │
│   ├── api/
│   │   ├── router.py                   # 汇总所有 APIRouter
│   │   └── routes/
│   │       ├── analysis.py             # /agent/analyze /patch /compare
│   │       ├── health.py
│   │       └── resource.py
│   │
│   ├── schemas/                        # Pydantic 边界模型，按业务域组织
│   │   ├── analysis.py                 # Request/Response + Command/Result
│   │   ├── messages.py                 # RabbitMQ JSON 契约
│   │   └── resource.py                 # resource advice models
│   │
│   ├── services/                       # 业务编排
│   │   ├── analysis.py                 # run_analysis()
│   │   └── resource_advice.py
│   │
│   ├── clients/                        # 外部服务 / SDK 客户端
│   │   ├── experiment.py               # experiment-service HTTP
│   │   ├── llm.py                      # OpenAI-compatible LLM
│   │   └── langfuse.py                 # Langfuse SDK
│   │
│   ├── messaging/
│   │   └── rabbitmq/
│   │       ├── topology.py              # exchange/queue/routing-key
│   │       ├── connection.py            # connection/channel/topology declaration
│   │       ├── consumer.py
│   │       └── publisher.py
│   │
│   ├── graph/                           # LangGraph 工作流
│   │   ├── state.py
│   │   ├── builder.py
│   │   ├── router.py
│   │   ├── nodes/
│   │   └── subgraphs/
│   │       ├── redis/
│   │       ├── rabbitmq/
│   │       ├── seata/
│   │       └── elasticsearch/
│   │
│   ├── prompts/                         # Langfuse Prompt 管理
│   ├── tools/                           # Agent tools
│   ├── core/                            # Settings 等基础配置
│   └── evaluation/                      # Langfuse Dataset / Eval
│
├── tests/                               # 后续补 unit / integration
├── .env
├── .env.example
├── requirements.txt
└── Dockerfile
```

### 为什么不拆 request/response/dto/vo 目录

Python / FastAPI 更常见的是按业务域组织 Pydantic models，例如：

```text
schemas/analysis.py
    AnalyzeRequest
    AnalyzeResponse
    PatchRequest
    PatchResponse
    CompareRequest
    CompareResponse
    AnalysisCommand
    AnalysisResult
```

类名已经表达模型语义，没有必要每个类单独建一个文件或目录。

## 3. 模型边界

```text
HTTP AnalyzeRequest
        |
        v
AnalysisCommand <----- AgentAnalysisTaskMessage (MQ)
        |
        v
services.analysis.run_analysis
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

命名含义：

```text
*Request / *Response   HTTP 边界
*Message               MQ 跨服务 JSON 契约
*Command / *Result     service 内部输入输出
*State                 LangGraph 运行时状态
ExperimentAnalysis     Java Entity / PO
ExperimentPatch        Java Entity / PO
```

Python Agent 不直接维护 Experiment MySQL Entity。

## 4. Java 持久化模型

数据库所有权属于 `experiment-service`：

```text
experiment.domain/
├── ExperimentAnalysis.java
└── ExperimentPatch.java

experiment.mapper/
├── ExperimentAnalysisMapper.java
└── ExperimentPatchMapper.java
```

Python 只通过 HTTP / MQ 与 Java 协作。

## 5. HTTP

```text
Frontend
   -> Gateway :8000
      -> /agent/**
         -> Agent :9500
```

第一版：

```text
POST /agent/analyze
POST /agent/patch
POST /agent/compare
POST /agent/resource/advice
GET  /agent/health
```

## 6. 自动分析 MQ

```text
Runner SUCCESS
   -> Experiment 保存 experiment_result
   -> 创建 experiment_analysis(CREATED)
   -> AgentAnalysisTaskProducer
   -> agent.analysis.exchange
   -> agent.analysis.queue
   -> Python consumer
   -> AnalysisCommand
   -> services.analysis.run_analysis
   -> LangGraph
   -> AnalysisResult
   -> AgentAnalysisStatusMessage
   -> agent.analysis.status.exchange
   -> agent.analysis.status.queue
   -> Experiment 持久化结果
```

可靠性：

1. exchange / queue durable。
2. Python consumer manual ACK + prefetch=1。
3. 最多尝试 3 次。
4. 重试耗尽 `reject(requeue=False)` -> DLX -> DLQ。
5. 最终 status/result 发布成功后才 ACK 原任务。
6. Java/Python RabbitMQ 拓扑参数必须完全一致。

## 7. MQ 只传轻量字段

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

不通过 MQ 传：

```text
代码正文
metricsJson
运行日志
完整 code diff
大段 report
```

由 `load_context` 收到 ID 后通过内部 HTTP 获取。

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

`AnalysisState` 仅用于图执行过程，不直接把 Request / Message 当 State。

## 9. 你后续主要实现

```text
services.analysis.run_analysis
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

## TODO

- [ ] 实现 `services.analysis.run_analysis`。
- [ ] 实现 Experiment Internal API 的 task/result/version/diff/logs 具体接口。
- [ ] 实现 Java AgentAnalysisStatusConsumer 状态机与幂等落库。
- [ ] 为 Evidence/Hypothesis/Patch 增加结构化 Pydantic 模型。
- [ ] 为 Redis 场景准备第一批 Langfuse Dataset。
- [ ] 增加 Java/Python MQ 契约测试。
- [ ] 增加 `tests/unit` 与 `tests/integration`。
