import asyncio
from copy import deepcopy

from fastapi.testclient import TestClient

from app.main import app
from app.services.player.runner import run_episode
from tests.fixtures.player.support import GridEnvironment, api_payload, episode_request

TOKEN = "test-only-python-agent-token-at-least-32-bytes"
HEADERS = {"X-Internal-Token": TOKEN, "X-Trace-Id": "trace-player-api-0001"}
client = TestClient(app)


def test_player_api_requires_internal_token_and_valid_protocol(monkeypatch):
    async def execute(payload):
        return await run_episode(payload, environment_factory=GridEnvironment)
    monkeypatch.setattr("app.routers.player.run_episode", execute)
    payload = api_payload(episode_request())

    assert client.post("/player/episodes/run", json=payload).status_code == 401
    response = client.post("/player/episodes/run", headers=HEADERS, json=payload)
    assert response.status_code == 200
    assert response.json()["audit"]["traceId"] == "trace-player-api-0001"
    invalid = deepcopy(payload)
    invalid["episodeProtocolVersion"] = "episode/2.0"
    assert client.post("/player/episodes/run", headers=HEADERS, json=invalid).status_code == 422


def test_batch_preserves_successful_sibling_and_marks_partial_failure(monkeypatch):
    async def execute(items, concurrency):
        assert concurrency == 2
        success = await run_episode(items[0], environment_factory=GridEnvironment)
        failed = deepcopy(success)
        failed["episodeId"] = items[1].episode_id
        failed["clientEpisodeKey"] = items[1].client_episode_key
        failed["executionStatus"] = "REJECTED"
        failed["terminationReason"] = None
        failed["outcome"] = None
        failed["error"] = {"phase": "VALIDATION", "code": "UNREGISTERED_POLICY", "message": "Policy reference is not registered", "retryable": False, "failedSequence": None}
        return [success, failed]
    monkeypatch.setattr("app.routers.player.run_episode_batch", execute)
    first = episode_request()
    second = episode_request(key="episode-2")
    payload = {"episodeProtocolVersion": "episode/1.0", "clientBatchKey": "batch-1", "concurrency": 2, "episodes": [api_payload(first), api_payload(second)]}
    response = client.post("/player/episodes/batch", headers=HEADERS, json=payload)
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "PARTIAL_SUCCESS"
    assert body["counts"]["completed"] == 1
    assert body["counts"]["rejected"] == 1
    assert body["results"][0]["steps"]


def test_api_rejects_duplicate_items_and_oversized_body():
    item = api_payload(episode_request())
    duplicate = {"episodeProtocolVersion": "episode/1.0", "clientBatchKey": "batch-1", "episodes": [item, item]}
    assert client.post("/player/episodes/batch", headers=HEADERS, json=duplicate).status_code == 422
    oversized = "x" * (2 * 1024 * 1024 + 1)
    assert client.post("/player/episodes/run", headers=HEADERS, content=oversized).status_code == 413


def test_cancellation_closes_environment_session():
    environment = GridEnvironment()
    class SlowPolicy:
        async def decide(self, observation):
            await asyncio.sleep(60)

    async def scenario():
        task = asyncio.create_task(run_episode(episode_request(), environment_factory=lambda: environment, policy=SlowPolicy()))
        await asyncio.sleep(0)
        await asyncio.sleep(0)
        task.cancel()
        try:
            await task
        except asyncio.CancelledError:
            pass

    asyncio.run(scenario())
    assert environment.closed is True
