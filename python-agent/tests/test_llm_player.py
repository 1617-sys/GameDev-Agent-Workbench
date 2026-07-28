import asyncio

from app.schemas.player import Action
from app.services.player.policies import PolicyFailure
from app.services.player.runner import replay_recorded_decisions, run_episode
from tests.fixtures.player.support import GridEnvironment, episode_request


def test_llm_second_decision_receives_first_environment_feedback():
    observations = []
    async def decide(observation):
        observations.append(observation)
        return {"action": {"type": "MOVE_RIGHT"}, "modelCallId": f"call-{len(observations)}", "usage": {"inputTokens": 2, "outputTokens": 1, "totalTokens": 3}, "providerLatencyMs": 1, "responseDigest": "b" * 64, "mock": True}

    result = asyncio.run(run_episode(episode_request(kind="LLM"), environment_factory=GridEnvironment, llm_decide=decide))
    assert result["outcome"] == "WON"
    assert observations[1]["step"] == 1
    assert observations[1]["player"]["position"]["x"] == 10
    assert result["audit"]["mock"] is True
    assert result["usage"]["status"] == "REPORTED"


def test_invalid_action_provider_timeout_and_model_budget_have_stable_failures():
    async def invalid(_): return {"action": {"type": "TELEPORT"}}
    invalid_result = asyncio.run(run_episode(episode_request(kind="LLM"), environment_factory=GridEnvironment, llm_decide=invalid))
    assert invalid_result["error"]["code"] == "INVALID_POLICY_OUTPUT"
    assert invalid_result["audit"]["modelCalls"][0]["status"] == "PARSE_ERROR"

    async def illegal_json(_): raise ValueError("invalid provider JSON containing private text")
    json_result = asyncio.run(run_episode(episode_request(kind="LLM"), environment_factory=GridEnvironment, llm_decide=illegal_json))
    assert json_result["error"]["code"] == "INVALID_POLICY_OUTPUT"
    assert "private text" not in json_result["error"]["message"]

    async def timeout(_): raise TimeoutError("provider secret body")
    timeout_result = asyncio.run(run_episode(episode_request(kind="LLM"), environment_factory=GridEnvironment, llm_decide=timeout))
    assert timeout_result["error"]["code"] == "MODEL_TIMEOUT"
    assert "provider secret body" not in timeout_result["error"]["message"]

    request = episode_request(kind="LLM")
    request.budgets.max_model_calls = 0
    exhausted = asyncio.run(run_episode(request, environment_factory=GridEnvironment, llm_decide=invalid))
    assert exhausted["error"]["code"] == "MODEL_BUDGET_EXHAUSTED"


def test_recorded_decision_replay_does_not_call_model():
    actions = [Action(type="MOVE_RIGHT") for _ in range(4)]
    result = asyncio.run(replay_recorded_decisions(episode_request(kind="LLM"), actions, environment_factory=GridEnvironment))
    assert result["outcome"] == "WON"
    assert result["audit"]["modelCalls"] == []
