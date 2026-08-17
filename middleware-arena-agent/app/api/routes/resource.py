"""Resource-advice HTTP routes."""

from fastapi import APIRouter

from app.schemas.resource import ResourceAdviceRequest, ResourceAdviceResponse
from app.services.resource_advice import calculate_resource_advice

router = APIRouter(prefix="/agent", tags=["resource"])


@router.post("/resource/advice", response_model=ResourceAdviceResponse)
def resource_advice(request: ResourceAdviceRequest) -> ResourceAdviceResponse:
    return calculate_resource_advice(request)
