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
- [ ] 根据实际 SubGraph 确定 reducer，尤其 hypotheses/evidence/patches 等累加字段。
- [ ] 明确哪些字段进入 Checkpoint，哪些大对象只保存 OSS/object-key 引用。
- [ ] 为 Evidence/Hypothesis/Patch 建结构化 Pydantic 模型，替代长期使用 dict。
"""

from typing import Any, TypedDict


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
    # analyze_code 节点产出的代码风险点，例如循环内 Redis GET / 深分页查询。
    code_findings: list[dict[str, Any]]
    # 多个候选故障假设；不能一上来就只保留一个答案。
    # 每个 hypothesis 后续应至少包含 type / evidence / confidence / verification。
    hypotheses: list[dict[str, Any]]
    # 支撑诊断结论的证据列表；最终报告中的结论必须能回指这些证据。
    evidence: list[dict[str, Any]]

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
    # Langfuse Trace ID，方便从业务分析记录跳到完整 Agent 执行链。
    trace_id: str
