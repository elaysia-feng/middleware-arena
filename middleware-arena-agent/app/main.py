from fastapi import FastAPI

from app.resource_advice import (
    ResourceAdviceRequest,
    ResourceAdviceResponse,
    calculate_resource_advice,
)

app = FastAPI(title="Middleware Arena AI Agent", version="0.1.0")


@app.get("/health")
def health() -> dict[str, str]:
    """健康检查"""
    return {"status": "ok"}


@app.post("/resource/advice", response_model=ResourceAdviceResponse)
def resource_advice(request: ResourceAdviceRequest) -> ResourceAdviceResponse:
    """结合规则预算、历史 P95 和 LLM 建议计算最终资源预算。"""
    return calculate_resource_advice(request)


# TODO[AI Agent]：挂载通用 /analyze 路由（LangGraph 实验报告流程）
