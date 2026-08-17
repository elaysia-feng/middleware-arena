"""FastAPI Router 汇总入口。

每个业务域在 ``app/api/routes`` 下维护自己的 Router，main.py 不需要知道具体有多少接口。
新增一组接口时：
1. 在 routes/ 新建或扩展业务模块。
2. 在这里 include_router。
3. main.py 保持只 include ``api_router`` 一次。

这样可以避免所有接口都堆到 main.py。
"""

from fastapi import APIRouter

from app.api.routes import analysis, health, resource

# 顶层 Router 本身不设置 prefix；每个子 Router 自己管理业务前缀。
api_router = APIRouter()

# 健康检查用于服务探活，不依赖 Agent 核心分析逻辑。
api_router.include_router(health.router)
# /agent/analyze、/agent/patch、/agent/compare。
api_router.include_router(analysis.router)
# /agent/resource/advice。
api_router.include_router(resource.router)
