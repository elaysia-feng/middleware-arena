from fastapi import FastAPI

app = FastAPI(title="Middleware Arena AI Agent", version="0.1.0")


@app.get("/health")
def health() -> dict[str, str]:
    """健康检查"""
    return {"status": "ok"}


# TODO[AI Agent]：挂载 /analyze 路由（LangGraph 流程），当前仅骨架
