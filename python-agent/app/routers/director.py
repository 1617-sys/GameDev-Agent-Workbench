from fastapi import APIRouter

from app.schemas.director import DirectorDecision, DirectorSnapshot
from app.services.director import decide

router = APIRouter(prefix="/director", tags=["director"])


@router.post("/decisions", response_model=DirectorDecision, response_model_by_alias=True)
async def next_decision(snapshot: DirectorSnapshot):
    return decide(snapshot)
