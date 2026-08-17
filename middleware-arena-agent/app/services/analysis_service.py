"""HTTP / MQ 共用的 Agent 分析业务入口。

这里刻意只保留 Service 业务编排，不定义 Request/Response/Message/State。

1. HTTP Request / MQ Message 都先转换为 AnalysisCommand。
2. `run_analysis` 是唯一 Agent 核心业务入口，后续在这里启动 LangGraph。
3. 返回 AnalysisResult，再由 HTTP / MQ 各自转换成自己的出参模型。

TODO[核心逻辑-由你实现]:
- [ ] load_context：通过 experiment internal API 拉 version / metrics / diff / logs。
- [ ] 编译并执行 LangGraph，接入 Redis/RabbitMQ/Seata/ES SubGraph。
- [ ] 注入 Langfuse callback / trace metadata。
- [ ] 生成 evidence / hypothesis / bottleneck / patch / report。
- [ ] 控制 max_iterations 与 Human-in-the-loop。
"""

from app.schemas.service.analysis_command import AnalysisCommand
from app.schemas.service.analysis_result import AnalysisResult


async def run_analysis(command: AnalysisCommand) -> AnalysisResult:
    """启动一次完整分析。

    基础设施已经把 HTTP/MQ 输入统一成 AnalysisCommand；
    接下来只需要在这里接 LangGraph，不要把分析逻辑写回 API 或 Consumer。
    """
    raise NotImplementedError(
        "TODO[Agent Core]: 在 app/services/analysis_service.py::run_analysis 中接入 LangGraph"
    )
