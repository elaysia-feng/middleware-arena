"""LangGraph 全局 ``AnalysisState``。

这个 State 是一次 Agent 分析在 LangGraph 节点之间传递的“共享上下文”。
它和 Pydantic ``BaseModel`` 的用途不同：

- Request/Message/Command：负责边界输入和类型校验。
- AnalysisState：负责 Graph 运行过程中节点之间共享数据。
- Entity：负责数据库映射。

为什么这里先用 ``TypedDict``：
1. LangGraph 原生支持 TypedDict State，写节点时仍然像普通 dict 一样方便。
2. IDE 能知道 ``state['metrics']`` 等字段是否存在、是什么类型。
3. ``total=False`` 表示这些字段允许“分阶段出现”，因为 load_context 前当然还没有 metrics/logs。

注意：字段多不代表每个节点都可以随便改全部字段。每个 Node 应该只读取自己需要的输入，
并只返回自己负责更新的字段。

TODO[核心逻辑-由你实现]:
- [ ] 为 patches 等后续并行累加字段补充 reducer。
- [ ] 明确哪些字段进入 Checkpoint，哪些大对象只保存 OSS/object-key 引用。
- [ ] 为 Evidence/Hypothesis/Patch 建结构化 Pydantic 模型，替代长期使用 dict。
"""

from typing import Annotated, Any, TypedDict


def merge_evidence_reducer(
    current: list[dict[str, Any]],
    update: list[dict[str, Any]],
) -> list[dict[str, Any]]:
    """合并并行节点证据；相同 ID 只保留最新内容，避免分支结果互相覆盖。"""
    merged: dict[str, dict[str, Any]] = {}
    for index, item in enumerate([*(current or []), *(update or [])]):
        if not isinstance(item, dict):
            continue
        evidence_id = str(item.get("id") or f"anonymous:{index}")
        merged[evidence_id] = item
    return list(merged.values())


def merge_hypotheses_reducer(
    current: list[dict[str, Any]],
    update: list[dict[str, Any]],
) -> list[dict[str, Any]]:
    """合并专家假设；相同 ID 的最新判断覆盖旧判断。"""
    merged: dict[str, dict[str, Any]] = {}
    for index, item in enumerate([*(current or []), *(update or [])]):
        if not isinstance(item, dict):
            continue
        hypothesis_id = str(
            item.get("id")
            or f"{item.get('expert', 'unknown')}:{item.get('type', 'unknown')}:{index}"
        )
        merged[hypothesis_id] = item
    return list(merged.values())


class AnalysisState(TypedDict, total=False):
    """一次中间件性能分析从开始到报告生成的完整运行时状态。"""

    # ==================================================================
    # 1. 身份 / 启动命令信息
    # 这些值主要来自 AnalysisCommand，通常在整个 Graph 生命周期中不改变。
    # ==================================================================

    # experiment_analysis.id；用于持久化状态、日志和 Langfuse Trace 关联。
    analysis_id: int
    # 当前被分析的 experiment_task.id。
    task_id: int
    # 任务所属用户，主要用于审计/配额；Graph 不应该根据它猜业务数据。
    user_id: int
    # 本次压测真正使用的 experiment_version.id，是代码 source of truth。
    version_id: int
    # 可选基线任务 ID；没有时 load_context 可以按策略查找历史基线。
    baseline_task_id: int
    # REDIS / RABBITMQ / SEATA / ELASTICSEARCH，用于 Router 选择专家 SubGraph。
    middleware_type: str
    # middleware_router 选择的下一跳，例如 redis_expert；供条件边和运行记录复用。
    middleware_route: str
    # 路由原因；未知中间件会明确记录进入通用分析分支的原因。
    middleware_route_reason: str
    # 第一版通常为 PERFORMANCE_DIAGNOSIS。
    analysis_type: str
    # AUTO / MANUAL / RETRY，记录这次分析如何被触发。
    trigger_type: str
    # MQ 投递唯一 ID，用于幂等；HTTP 手工分析时可能不存在。
    dispatch_id: str

    # ==================================================================
    # 2. load_context 拉取的原始上下文
    # 原则：原始数据尽量保留，分析结论放到后面的 findings/hypotheses，不要相互覆盖。
    # ==================================================================

    # 实验运行配置，例如并发数、持续时间、容器资源等。
    config: dict[str, Any]
    # 当前 experiment_version 中参与实验的文件信息/代码内容或文件引用。
    files: list[dict[str, Any]]
    # 当前版本和基线版本之间的代码 Diff；用于定位性能变化是否由代码修改引入。
    code_diff: list[dict[str, Any]]
    # 当前实验原始/结构化性能指标，例如 QPS/P95/Error/CPU/Memory。
    metrics: dict[str, Any]
    # 基线实验的对应指标，用于做 delta/ratio 比较。
    baseline_metrics: dict[str, Any]
    # Runner/应用运行日志；后续数据量大时应只存筛选后的日志或 object key。
    logs: list[str]

    # ==================================================================
    # 3. 分析中间结果
    # 这些是“证据加工结果”，还不是最终结论。
    # ==================================================================

    # analyze_metrics 节点产出的指标异常，例如 P95 +300%、CPU 基本不变。
    metric_findings: list[dict[str, Any]]
    # analyze_logs 节点按异常类型聚合的日志发现，例如 OOM、超时、连接池耗尽。
    log_findings: list[dict[str, Any]]
    # analyze_code 节点产出的代码风险点，例如循环内 Redis GET / 深分页查询。
    code_findings: list[dict[str, Any]]
    # retrieve_similar 节点返回的历史相似实验，按相似度从高到低排列。
    similar_experiments: list[dict[str, Any]]
    # 多个候选故障假设；不能一上来就只保留一个答案。
    # 每个 hypothesis 后续应至少包含 type / evidence / confidence / verification。
    hypotheses: Annotated[list[dict[str, Any]], merge_hypotheses_reducer]
    # 支撑诊断结论的证据列表；最终报告中的结论必须能回指这些证据。
    evidence: Annotated[list[dict[str, Any]], merge_evidence_reducer]
    # merge_evidence 节点去重、排序后的证据，后续专家和裁决节点优先读取该字段。
    merged_evidence: list[dict[str, Any]]
    # 证据数量、来源覆盖、数据缺口和主要信号摘要。
    evidence_summary: dict[str, Any]
    # Redis 专家从全局证据中筛出的专项信号。
    redis_signals: list[dict[str, Any]]
    # Redis 深度诊断 Tool 返回的数据及可用性说明。
    redis_diagnostics: dict[str, Any]
    # Redis 专家结构化输出；只提供候选假设，不替代全局瓶颈裁决。
    redis_expert_result: dict[str, Any]
    # RabbitMQ 专家筛选结果、实例诊断和执行摘要。
    rabbitmq_signals: list[dict[str, Any]]
    rabbitmq_diagnostics: dict[str, Any]
    rabbitmq_expert_result: dict[str, Any]
    # Seata 专家筛选结果、关联数据库诊断和执行摘要。
    seata_signals: list[dict[str, Any]]
    seata_diagnostics: dict[str, Any]
    seata_expert_result: dict[str, Any]
    # Elasticsearch 专家筛选结果和执行摘要。
    elasticsearch_signals: list[dict[str, Any]]
    elasticsearch_diagnostics: dict[str, Any]
    elasticsearch_expert_result: dict[str, Any]
    # 未知中间件进入通用专家后的执行摘要。
    generic_expert_result: dict[str, Any]
    # generate_hypothesis 归并专家候选后交给 Judge 的假设列表。
    ranked_hypotheses: list[dict[str, Any]]
    hypothesis_summary: dict[str, Any]
    # judge_bottleneck 的完整裁决状态、理由和限制。
    judgement: dict[str, Any]
    # Judge 后的分支：高置信度生成候选 Patch，否则直接生成报告。
    confidence_route: str
    confidence_route_reason: str
    patch_confidence_threshold: float

    # ==================================================================
    # 4. 最终判断 / 优化建议
    # ==================================================================

    # judge_bottleneck 选出的主要瓶颈，例如 REDIS_N_PLUS_ONE。
    bottleneck: dict[str, Any]
    # 对最终瓶颈判断的置信度，建议统一 0~1。
    confidence: float
    # 不一定需要改代码的优化建议，例如调整 prefetch / pipeline / index。
    suggestions: list[dict[str, Any]]
    # Agent 生成的候选代码 Patch；只生成，不在 Graph 内偷偷应用。
    patches: list[dict[str, Any]]
    # Patch 节点的生成状态、限制和建议验证步骤。
    patch_summary: dict[str, Any]
    # HUMAN_REVIEW / COLLECT_MORE_EVIDENCE / REVIEW_REPORT。
    next_action: str

    # ==================================================================
    # 5. 自动优化循环 / 最终输出
    # ==================================================================

    # 当前是第几轮“分析 -> Patch -> 重跑”。
    iteration: int
    # 最大自动优化轮数，防止 Agent 无限制循环。
    max_iterations: int
    # V1 vs V2 的性能比较结果，例如 QPS/P95 delta 和优化是否生效。
    comparison: dict[str, Any]
    # 给用户看的最终 Markdown/结构化诊断报告。
    report: str
    # 报告标题、摘要、Prompt 版本和生成来源。
    report_summary: dict[str, Any]
    # Langfuse Trace ID，方便从业务分析记录跳到完整 Agent 执行链。
    trace_id: str
