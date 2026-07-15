import asyncio
import json

import pytest

from app.schemas.agent import AgentMockRequest
from app.services.langchain_agent import run_game_config_agent, run_langchain_agent


def payload() -> AgentMockRequest:
    return AgentMockRequest(
        project_uuid="project",
        title="GAME_GENERATE",
        content=json.dumps({
            "theme": "museum",
            "durationSeconds": 90,
            "difficulty": "normal",
            "visualTheme": "neon",
            "additionalRequirements": "two collectibles",
        }),
        context="upstream",
    )


def test_explicit_mock_mode_repeats_the_complete_generation_chain(monkeypatch):
    monkeypatch.setenv("AGENT_MOCK_MODE", "true")
    concept = asyncio.run(run_langchain_agent("GAME_CONCEPT", payload()))
    loop_payload = payload().model_copy(update={"context": concept.content})
    loop = asyncio.run(run_langchain_agent("CORE_LOOP_DESIGN", loop_payload))
    task_payload = payload().model_copy(update={"context": concept.content + "\n" + loop.content})
    tasks = asyncio.run(run_langchain_agent("TASK_BREAKDOWN", task_payload))
    config_payload = payload().model_copy(update={"context": concept.content + "\n" + loop.content + "\n" + tasks.content})
    first = asyncio.run(run_game_config_agent(config_payload))
    second = asyncio.run(run_game_config_agent(payload()))

    assert concept.model == loop.model == tasks.model == first.model == "mock"
    assert first.content == second.content
    assert first.game_config == second.game_config
    assert first.game_config["metadata"]["schemaVersion"] == "2.0"
    assert first.game_config["metadata"]["gameType"] == "arcade_collect"


def test_real_mode_does_not_silently_fall_back_when_model_setup_fails(monkeypatch):
    monkeypatch.delenv("AGENT_MOCK_MODE", raising=False)
    monkeypatch.delenv("LLM_API_KEY", raising=False)
    with pytest.raises(RuntimeError, match="LLM_API_KEY is missing"):
        asyncio.run(run_game_config_agent(payload()))
