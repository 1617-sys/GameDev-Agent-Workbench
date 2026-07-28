import asyncio

from app.schemas.player import Action, Observation
from app.services.player.policies import PERSONAS, DeterministicPlayerPolicy
from app.services.player.evaluation import summarize_episode_results
from app.services.player.runner import run_episode
from tests.fixtures.player.support import GridEnvironment, episode_request


def test_persona_parameters_and_policy_seed_are_stable_and_replayable():
    assert len({PERSONAS[name].digest for name in ("NOVICE", "REGULAR", "EXPERT")}) == 3
    assert PERSONAS["NOVICE"].vision_radius_px < PERSONAS["REGULAR"].vision_radius_px < PERSONAS["EXPERT"].vision_radius_px
    assert PERSONAS["NOVICE"].action_error_permille > PERSONAS["REGULAR"].action_error_permille > PERSONAS["EXPERT"].action_error_permille

    request = episode_request(persona_id="REGULAR", policy_seed=77)
    first = asyncio.run(run_episode(request, environment_factory=GridEnvironment))
    second = asyncio.run(run_episode(request, environment_factory=GridEnvironment))
    assert [step["decision"] for step in first["steps"]] == [step["decision"] for step in second["steps"]]
    assert first["trajectoryDigest"] == second["trajectoryDigest"]


def test_personas_have_explainable_aggregate_decision_frequency_differences():
    observation = Observation.model_validate({
        "protocolVersion": "simulation/1.0", "episodeId": "episode", "step": 0,
        "stateHash": "a" * 64, "status": "RUNNING", "terminationReason": None,
        "player": {"position": {"x": 0, "y": 0}}, "progress": {"exitUnlocked": False},
        "visibleEntities": [{"type": "collectible", "id": "item", "position": {"x": 100, "y": 0}}],
    })

    async def actions(name):
        policy = DeterministicPlayerPolicy(PERSONAS[name], 5)
        return [(await policy.decide(observation)).action.type.value for _ in range(12)]

    novice = asyncio.run(actions("NOVICE"))
    expert = asyncio.run(actions("EXPERT"))
    assert novice.count("WAIT") >= expert.count("WAIT")
    assert PERSONAS["NOVICE"].decision_interval_steps > PERSONAS["EXPERT"].decision_interval_steps


def test_persona_report_contains_versionable_aggregate_metrics_and_failures():
    results = [
        {"outcome": "WON", "acceptedActionCount": 4, "invalidActionCount": 0, "timing": {"wallDurationMs": 20}, "error": None, "terminationReason": "WON"},
        {"outcome": "ERROR", "acceptedActionCount": 2, "invalidActionCount": 1, "timing": {"wallDurationMs": 40}, "error": {"code": "DECISION_TIMEOUT"}, "terminationReason": None},
    ]
    report = summarize_episode_results(results, optimal_steps=4)
    assert report == {
        "sampleSource": "MACHINE", "sampleSize": 2, "completionRatePermille": 500,
        "meanWallDurationMs": 30, "invalidActionRatePermille": 142,
        "pathEfficiencyPermille": 666, "failureReasons": {"DECISION_TIMEOUT": 1},
    }
