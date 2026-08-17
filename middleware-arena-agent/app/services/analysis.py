"""Analysis business orchestration.

HTTP and RabbitMQ inputs are both converted to AnalysisCommand before entering this
module. Core reasoning stays in LangGraph; routes and consumers should not duplicate it.
"""

from app.schemas.analysis import AnalysisCommand, AnalysisResult


async def run_analysis(command: AnalysisCommand) -> AnalysisResult:
    """Run one complete middleware performance analysis."""
    # TODO[Agent Core - 由你实现]:
    # 1. load_context：通过 experiment internal API 拉 version / metrics / diff / logs。
    # 2. 编译并执行 LangGraph，接入 Redis/RabbitMQ/Seata/ES SubGraph。
    # 3. 注入 Langfuse callback / trace metadata。
    # 4. 生成 evidence / hypothesis / bottleneck / patch / report。
    # 5. 控制 max_iterations 与 Human-in-the-loop。
    raise NotImplementedError(
        "TODO[Agent Core]: 在 app/services/analysis.py::run_analysis 中接入 LangGraph"
    )
