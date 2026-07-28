import asyncio
import json

import httpx
import pytest

from app.clients.simulation_client import (
    SimulationAuthenticationError,
    SimulationClientError,
    SimulationEnvironmentClient,
    SimulationProtocolError,
    SimulationTransportError,
)
from app.schemas.player import Action, CreateSessionRequest, ObservationPolicy


TOKEN = "test-simulation-client-token-at-least-32-bytes"
SESSION_ID = "2d610a52-b30c-4d28-9d0e-a123edb49d8f"
EPISODE_ID = "00000000-0000-4000-8000-000000000101"
HASH_0 = "a" * 64
HASH_1 = "b" * 64


def request() -> CreateSessionRequest:
    return CreateSessionRequest(
        episode_id=EPISODE_ID,
        correlation_id="run-101",
        config_digest="c" * 64,
        seed=101,
        max_steps=100,
        observation_policy=ObservationPolicy(kind="FULL"),
        game_config={"schemaVersion": "game-config/2.0"},
    )


def observation(step: int = 0, state_hash: str = HASH_0) -> dict:
    return {
        "protocolVersion": "simulation/1.0",
        "episodeId": EPISODE_ID,
        "step": step,
        "stateHash": state_hash,
        "status": "RUNNING",
        "terminationReason": None,
    }


def response(status: int, payload: dict | str) -> httpx.Response:
    if isinstance(payload, str):
        return httpx.Response(status, content=payload, headers={"content-type": "application/json"})
    return httpx.Response(status, json=payload)


def run(coro):
    return asyncio.run(coro)


def test_maps_create_observe_step_and_close_with_protocol_headers():
    calls: list[httpx.Request] = []

    async def handler(http_request: httpx.Request) -> httpx.Response:
        calls.append(http_request)
        assert http_request.headers["x-internal-token"] == TOKEN
        if http_request.method == "POST" and http_request.url.path == "/v1/sessions":
            assert json.loads(http_request.content)["episodeId"] == EPISODE_ID
            return response(201, {
                "protocolVersion": "simulation/1.0", "sessionId": SESSION_ID,
                "episodeId": EPISODE_ID, "expiresAt": 1234, "observation": observation(),
            })
        if http_request.method == "GET":
            return response(200, {"protocolVersion": "simulation/1.0", "sessionId": SESSION_ID, "observation": observation()})
        if http_request.method == "DELETE":
            return response(200, {"protocolVersion": "simulation-service/1.0", "sessionId": SESSION_ID, "closed": True})
        action = json.loads(http_request.content)["action"]
        assert action == {"type": "WAIT"}
        return response(200, {
            "protocolVersion": "simulation/1.0", "sessionId": SESSION_ID,
            "stepResult": {
                "protocolVersion": "simulation/1.0", "episodeId": EPISODE_ID, "step": 1,
                "accepted": True, "advanced": True, "previousStateHash": HASH_0,
                "stateHash": HASH_1, "status": "RUNNING", "terminationReason": None,
                "observation": observation(1, HASH_1),
            },
        })

    async def scenario():
        transport = httpx.MockTransport(handler)
        async with httpx.AsyncClient(transport=transport) as http_client:
            environment = SimulationEnvironmentClient(base_url="http://simulation", internal_token=TOKEN, client=http_client)
            initial = await environment.reset(request())
            assert initial.step == 0
            assert (await environment.observe()).state_hash == HASH_0
            assert (await environment.step(Action(type="WAIT"))).state_hash == HASH_1
            await environment.close()
            assert environment.session_id is None

    run(scenario())
    assert [call.method for call in calls] == ["POST", "GET", "POST", "DELETE"]


def test_maps_401_without_exposing_the_response_body():
    async def handler(_: httpx.Request) -> httpx.Response:
        return response(401, {"error": {"code": "UNAUTHORIZED", "message": "secret upstream details", "retriable": False}})

    async def scenario():
        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http_client:
            client = SimulationEnvironmentClient(base_url="http://simulation", internal_token=TOKEN, client=http_client)
            with pytest.raises(SimulationAuthenticationError) as captured:
                await client.reset(request())
            assert captured.value.code == "UNAUTHORIZED"
            assert "secret upstream" not in str(captured.value)

    run(scenario())


def test_enforces_total_timeout_without_blocking_the_event_loop():
    marker = False

    async def handler(_: httpx.Request) -> httpx.Response:
        nonlocal marker
        await asyncio.sleep(0.03)
        marker = True
        return response(500, {})

    async def scenario():
        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http_client:
            client = SimulationEnvironmentClient(
                base_url="http://simulation", internal_token=TOKEN, client=http_client,
                total_timeout=0.005,
            )
            with pytest.raises(SimulationTransportError) as captured:
                await client.reset(request())
            assert captured.value.code == "SIMULATION_TIMEOUT"

    run(scenario())
    assert marker is False


@pytest.mark.parametrize("payload", ["not-json", {"protocolVersion": "simulation/2.0"}])
def test_rejects_invalid_json_and_incompatible_protocol(payload):
    async def handler(_: httpx.Request) -> httpx.Response:
        return response(201, payload)

    async def scenario():
        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http_client:
            client = SimulationEnvironmentClient(base_url="http://simulation", internal_token=TOKEN, client=http_client)
            with pytest.raises(SimulationProtocolError) as captured:
                await client.reset(request())
            assert captured.value.code == "INVALID_SIMULATION_RESPONSE"

    run(scenario())


def test_retries_observe_but_never_replays_an_unsafe_step():
    counts = {"observe": 0, "step": 0}

    async def handler(http_request: httpx.Request) -> httpx.Response:
        if http_request.url.path == "/v1/sessions":
            return response(201, {
                "protocolVersion": "simulation/1.0", "sessionId": SESSION_ID,
                "episodeId": EPISODE_ID, "expiresAt": 1234, "observation": observation(),
            })
        operation = "observe" if http_request.method == "GET" else "step"
        counts[operation] += 1
        if operation == "observe" and counts[operation] == 1:
            return response(503, {"error": {"code": "BUSY", "retriable": True}})
        if operation == "observe":
            return response(200, {"protocolVersion": "simulation/1.0", "sessionId": SESSION_ID, "observation": observation()})
        return response(503, {"error": {"code": "UNAVAILABLE", "retriable": True}})

    async def scenario():
        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http_client:
            client = SimulationEnvironmentClient(base_url="http://simulation", internal_token=TOKEN, client=http_client, safe_retries=2)
            await client.reset(request())
            await client.observe()
            with pytest.raises(SimulationClientError):
                await client.step(Action(type="WAIT"))

    run(scenario())
    assert counts == {"observe": 2, "step": 1}


def test_context_manager_closes_after_error_and_reports_close_failure_on_success():
    closes = 0

    async def handler(http_request: httpx.Request) -> httpx.Response:
        nonlocal closes
        if http_request.url.path == "/v1/sessions":
            return response(201, {
                "protocolVersion": "simulation/1.0", "sessionId": SESSION_ID,
                "episodeId": EPISODE_ID, "expiresAt": 1234, "observation": observation(),
            })
        closes += 1
        return response(503, {"error": {"code": "CLOSE_FAILED", "retriable": False}})

    async def failing_body():
        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http_client:
            with pytest.raises(RuntimeError, match="policy failed"):
                async with SimulationEnvironmentClient(base_url="http://simulation", internal_token=TOKEN, client=http_client, safe_retries=0) as client:
                    await client.reset(request())
                    raise RuntimeError("policy failed")

    async def successful_body():
        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http_client:
            with pytest.raises(SimulationClientError) as captured:
                async with SimulationEnvironmentClient(base_url="http://simulation", internal_token=TOKEN, client=http_client, safe_retries=0) as client:
                    await client.reset(request())
            assert captured.value.code == "CLOSE_FAILED"

    run(failing_body())
    run(successful_body())
    assert closes == 2
