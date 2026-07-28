import asyncio

from app.services.player.runner import run_episode
from tests.fixtures.player.support import GridEnvironment, episode_request


def test_deterministic_player_observes_each_step_and_wins_without_coordinate_answers():
    environments = []
    def factory():
        environment = GridEnvironment()
        environments.append(environment)
        return environment

    first = asyncio.run(run_episode(episode_request(), environment_factory=factory))
    second = asyncio.run(run_episode(episode_request(), environment_factory=factory))

    assert first["executionStatus"] == "COMPLETED"
    assert first["outcome"] == "WON"
    assert first["usage"]["status"] == "NOT_APPLICABLE"
    assert [step["decision"]["requestedAction"] for step in first["steps"]] == [step["decision"]["requestedAction"] for step in second["steps"]]
    assert first["finalStateHash"] == second["finalStateHash"]
    assert all(step["observationDigest"] for step in first["steps"])
    assert all(environment.closed for environment in environments)


def test_environment_and_budget_failures_have_stable_domains():
    environment = GridEnvironment(fail_observe=True)
    dependency = asyncio.run(run_episode(episode_request(), environment_factory=lambda: environment))
    assert dependency["error"]["phase"] == "DEPENDENCY"
    assert dependency["error"]["code"] == "ENV_DOWN"
    assert "hidden body" not in dependency["error"]["message"]
    assert environment.closed is True

    budget = asyncio.run(run_episode(episode_request(max_decisions=2), environment_factory=GridEnvironment))
    assert budget["executionStatus"] == "FAILED"
    assert budget["error"]["code"] == "DECISION_BUDGET_EXHAUSTED"
