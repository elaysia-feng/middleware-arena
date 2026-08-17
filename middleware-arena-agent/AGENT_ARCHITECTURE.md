# Middleware Arena Agent 架构骨架

## 1. 目标

1. 读取实验版本、代码 Diff、压测指标和运行日志。
2. 通过 LangGraph + 中间件 SubGraph 生成有证据的性能瓶颈假设。
3. 通过 Langfuse 完成 Trace、Prompt Management、Dataset、Experiment、Evaluator。
4. 生成候选代码 Patch，但默认不直接改代码，由用户确认后创建新版本。
5. 新版本再次压测后，对比 Before / After 判断优化是否有效。

## 2. 推荐主流程

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

## 3. 目录职责

```text
app/
├── api/                 # FastAPI 接口：分析、Patch、优化前后对比
├── graph/
│   ├── builder.py       # 主 LangGraph
│   ├── state.py         # 全局 AnalysisState
│   ├── router.py        # 条件路由
│   ├── nodes/           # 通用分析节点
│   └── subgraphs/       # Redis / RabbitMQ / Seata / Elasticsearch 专家图
├── prompts/             # Langfuse Prompt 获取与名称管理
├── tools/               # Experiment / Metrics / Logs 等 Agent Tool
├── services/            # LLM / Langfuse / 外部服务客户端
└── evaluation/          # Langfuse Dataset / Experiment / Evaluator
```

## 4. 实现顺序

1. 先完成 `graph/state.py` 与 `load_context.py`，打通 experiment-service 数据获取。
2. 完成 `analyze_metrics.py + analyze_code.py + generate_hypothesis.py`，先跑通 Redis 场景。
3. 接入 Langfuse Trace 和 Prompt Management，禁止 Prompt 散落在 Node 内。
4. 完成 `judge_bottleneck.py + generate_report.py`，形成第一版只读分析闭环。
5. 再完成 `generate_patch.py` 与前端 Human-in-the-loop。
6. 最后增加 RabbitMQ / Seata / Elasticsearch SubGraph 和 Langfuse Eval。

## 5. 关键约束

1. `experiment_version + OSS` 是本次实验代码的 source of truth，不以 GitHub HEAD 作为实验代码来源。
2. Agent 只生成 Patch；用户确认后由 experiment-service 创建新版本。
3. 所有瓶颈结论必须关联 evidence，禁止只有自然语言猜测。
4. 自动优化循环必须限制 `max_iterations`，避免无限压测。
5. Langfuse 故障只能影响可观测与评测，不能阻断核心分析流程。

## TODO

- [ ] 将现有 `app/state.py` 迁移到 `app/graph/state.py`。
- [ ] 在 `main.py` 挂载新的 API Router。
- [ ] 增加 Langfuse / experiment-service / runner 的环境变量配置。
- [ ] 增加 schemas 与 client 实现。
- [ ] 为 Redis 场景准备第一批 Dataset。
- [ ] 增加 graph / tool / evaluator 单元测试。
