"""Application API router composition."""

from fastapi import APIRouter

from app.api.routes import analysis, health, resource

api_router = APIRouter()
api_router.include_router(health.router)
api_router.include_router(analysis.router)
api_router.include_router(resource.router)
