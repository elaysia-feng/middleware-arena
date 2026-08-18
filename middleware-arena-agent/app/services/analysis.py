"""Agent 分析业务编排入口。

HTTP 和 RabbitMQ 是两种不同的“入口协议”，但最终都必须进入这个 Service：

HTTP AnalyzeRequest --------┐
                            ├─> AnalysisCommand -> run_analysis -> AnalysisResult
MQ AgentAnalysisTaskMessage ┘

这样做的目的：
1. HTTP/MQ 不会各写一套 LangGraph 调用逻辑。
2. Consumer 只负责消息可靠性，Router 只负责 HTTP，不承担 AI 业务。
3. 以后单元测试可以直接构造 AnalysisCommand 测试核心分析，不需要真的启动 FastAPI/RabbitMQ。

真正的性能诊断逻辑继续放在 ``app.graph``，这个文件只做业务流程编排。
"""

from app.schemas.analysis import AnalysisCommand, AnalysisResult


async def run_analysis(command: AnalysisCommand) -> AnalysisResult:
    """启动一次完整的中间件性能分析。

    参数：
        command: 已经由 HTTP/MQ 转换好的统一业务命令。

    返回：
        AnalysisResult。上层再决定转成 HTTP Response 还是 MQ Status Message。

    这里最终应该负责“串流程”，而不是把所有节点代码直接写进一个函数。
    """

    # TODO[Agent Core - 由你实现]:
    # 1. 根据 command 构造最小初始 AnalysisState。
    # 2. 调 graph/builder.py 获取已经 compile 的 LangGraph。
    # 3. invoke/ainvoke Graph，由 load_context 自己通过 Tool 拉取 version / metrics / diff / logs。
    # 4. 注入 Langfuse callback / trace metadata：analysisId、taskId、versionId 等。
    # 5. Graph 内部完成 metrics/code/subgraph/hypothesis/judge/patch/report。
    # 6. 从最终 AnalysisState 提取协议无关的 AnalysisResult。
    # 7. 自动优化循环最多执行 settings.agent_max_analysis_iterations 次。
    # 8. 任何“真正应用 Patch / 创建新 Version”的写操作都必须经过 Human-in-the-loop。

    raise NotImplementedError(
        "TODO[Agent Core]: 在 app/services/analysis.py::run_analysis 中接入 LangGraph"
    )
