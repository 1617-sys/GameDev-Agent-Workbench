from typing import Any

from fastapi import APIRouter, Request

from app.schemas.player import PlayerEpisodeBatchRequest, PlayerEpisodeRequest
from app.services.player.policies import canonical_digest
from app.services.player.runner import run_episode, run_episode_batch

router = APIRouter(prefix="/player", tags=["player"])


def _trace(result: dict[str, Any], request: Request) -> dict[str, Any]:
    result["audit"]["traceId"] = getattr(request.state, "trace_id", result["audit"].get("traceId"))
    return result


@router.post("/episodes/run")
async def execute_episode(payload: PlayerEpisodeRequest, request: Request):
    return _trace(await run_episode(payload), request)


@router.post("/episodes/batch")
async def execute_episode_batch(payload: PlayerEpisodeBatchRequest, request: Request):
    results = await run_episode_batch(payload.episodes, payload.concurrency)
    results = [_trace(result, request) for result in results]
    counts = {"total": len(results), "queued": 0, "running": 0, "completed": 0, "failed": 0, "rejected": 0, "cancelled": 0}
    for result in results:
        counts[result["executionStatus"].lower()] += 1
    status = "SUCCEEDED" if counts["completed"] == counts["total"] else "PARTIAL_SUCCESS" if counts["completed"] else "FAILED"
    return {
        "episodeProtocolVersion": "episode/1.0",
        "batchId": payload.episodes[0].batch_id,
        "clientBatchKey": payload.client_batch_key,
        "requestFingerprint": canonical_digest(payload.model_dump(by_alias=True, mode="json")),
        "status": status,
        "counts": counts,
        "items": [{
            "clientEpisodeKey": result["clientEpisodeKey"], "episodeId": result["episodeId"],
            "executionStatus": result["executionStatus"], "resultRef": None, "error": result["error"],
        } for result in results],
        "results": results,
    }
