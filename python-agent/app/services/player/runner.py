import asyncio
import time
from collections.abc import Callable
from typing import Any

from app.clients.llm_client import LLMClient
from app.clients.simulation_client import SimulationClientError, SimulationEnvironmentClient
from app.prompts.player import PLAYER_PROMPT_DIGEST, PLAYER_PROMPT_VERSION
from app.schemas.player import Action, CreateSessionRequest, PlayerEpisodeRequest
from app.services.player.policies import (
    DETERMINISTIC_POLICY_DIGEST,
    LLM_POLICY_DIGEST,
    PERSONAS,
    DeterministicPlayerPolicy,
    LlmPlayerPolicy,
    PlayerPolicy,
    PolicyFailure,
    RecordedDecisionPolicy,
    canonical_digest,
)


def _error(phase: str, code: str, message: str, sequence: int | None = None, retryable: bool = False) -> dict[str, Any]:
    return {"phase": phase, "code": code, "message": message, "retryable": retryable, "failedSequence": sequence}


def _usage(status: str = "NOT_APPLICABLE", reason: str | None = None) -> dict[str, Any]:
    return {
        "status": status, "inputTokens": None, "outputTokens": None, "totalTokens": None,
        "costMicros": None, "currency": None, "providerLatencyMs": None, "unavailableReason": reason,
    }


def _outcome(reason: str | None) -> str | None:
    if reason == "WON": return "WON"
    if reason in ("HEALTH_DEPLETED", "TIME_EXPIRED"): return "LOST"
    if reason == "MAX_STEPS": return "TRUNCATED"
    if reason == "ERROR": return "ERROR"
    return None


def validate_registered_request(request: PlayerEpisodeRequest) -> tuple[Any, dict[str, Any] | None]:
    persona = PERSONAS.get(request.persona.persona_id)
    if persona is None or request.persona.persona_version != persona.version or request.persona.persona_digest != persona.digest:
        return None, _error("VALIDATION", "UNREGISTERED_PERSONA", "Persona reference is not registered")
    expected_policy = DETERMINISTIC_POLICY_DIGEST if request.policy.kind == "DETERMINISTIC" else LLM_POLICY_DIGEST
    expected_id = "deterministic-heuristic" if request.policy.kind == "DETERMINISTIC" else "llm-step"
    if request.policy.policy_id != expected_id or request.policy.policy_version != "1.0" or request.policy.policy_digest != expected_policy:
        return None, _error("VALIDATION", "UNREGISTERED_POLICY", "Policy reference is not registered")
    policy = request.simulation.observation_policy
    if persona.persona_id == "baseline-neutral":
        if policy.kind != "FULL": return None, _error("VALIDATION", "OBSERVATION_POLICY_MISMATCH", "Baseline persona requires FULL observation")
    elif policy.kind != "PERSONA" or policy.vision_radius_px != persona.vision_radius_px:
        return None, _error("VALIDATION", "OBSERVATION_POLICY_MISMATCH", "Observation policy does not match the registered Persona")
    if request.model and request.model.provider == "mock":
        return None, _error("VALIDATION", "MOCK_MODEL_NOT_ALLOWED", "Mock models are test-only and cannot be requested through the API")
    return persona, None


def _base_result(request: PlayerEpisodeRequest) -> dict[str, Any]:
    return {
        "episodeProtocolVersion": "episode/1.0", "episodeId": request.episode_id, "batchId": request.batch_id,
        "clientEpisodeKey": request.client_episode_key, "sampleSource": "MACHINE",
        "prototype": request.prototype.model_dump(by_alias=True, mode="json"),
        "simulation": request.simulation.model_dump(by_alias=True, mode="json"),
        "policy": request.policy.model_dump(by_alias=True, mode="json"),
        "persona": request.persona.model_dump(by_alias=True, mode="json", exclude={"policy_seed"}),
        "model": request.model.model_dump(by_alias=True, mode="json") if request.model else None,
        "metricVersion": request.metric_version, "executionStatus": "REJECTED", "terminationReason": None,
        "outcome": None, "stepCount": 0, "acceptedActionCount": 0, "invalidActionCount": 0,
        "finalStateHash": None, "finalScore": None, "trajectoryDigest": None, "steps": [],
        "usage": _usage("NOT_APPLICABLE" if request.policy.kind == "DETERMINISTIC" else "UNAVAILABLE", "NOT_INVOKED" if request.policy.kind == "LLM" else None),
        "timing": {"queuedMs": 0, "wallDurationMs": None, "simulationDurationMs": None, "policyDurationMs": None},
        "error": None,
        "audit": {
            "traceId": request.correlation_id, "mock": False, "modelCalls": [],
            "policyDigest": request.policy.policy_digest, "personaDigest": request.persona.persona_digest,
            "promptVersion": PLAYER_PROMPT_VERSION if request.policy.kind == "LLM" else None,
            "promptDigest": PLAYER_PROMPT_DIGEST if request.policy.kind == "LLM" else None,
        },
    }


async def run_episode(
    request: PlayerEpisodeRequest,
    *,
    environment_factory: Callable[[], Any] | None = None,
    policy: PlayerPolicy | None = None,
    llm_decide: Callable[[dict[str, Any]], Any] | None = None,
) -> dict[str, Any]:
    """Run one bounded, replayable player episode.

    The environment advances only through explicit observe/step calls. Wall-clock
    measurements are evidence and timeout controls; they never participate in the
    deterministic game state or trajectory digest.
    """
    result = _base_result(request)
    persona, validation_error = validate_registered_request(request)
    if validation_error:
        result["error"] = validation_error
        return result
    if policy is None:
        if request.policy.kind == "DETERMINISTIC":
            policy = DeterministicPlayerPolicy(persona, request.persona.policy_seed)
        else:
            decide = llm_decide or LLMClient().decide_player_action
            policy = LlmPlayerPolicy(decide, persona, request.budgets.max_model_calls)
    environment_factory = environment_factory or SimulationEnvironmentClient
    started = time.perf_counter()
    policy_duration = 0
    simulation_duration = 0
    restarts = 0
    last_observation = None

    try:
        async with asyncio.timeout(request.budgets.wall_timeout_ms / 1000):
            async with environment_factory() as environment:
                simulation_started = time.perf_counter()
                last_observation = await environment.reset(CreateSessionRequest(
                    episode_id=request.episode_id, correlation_id=request.correlation_id,
                    config_digest=request.prototype.config_digest, seed=request.simulation.seed,
                    max_steps=request.simulation.max_steps, observation_policy=request.simulation.observation_policy,
                    game_config=request.game_config,
                ))
                simulation_duration += int((time.perf_counter() - simulation_started) * 1000)
                result["executionStatus"] = "FAILED"
                for sequence in range(1, request.budgets.max_decisions + 1):
                    simulation_started = time.perf_counter()
                    observation = await environment.observe(correlation_id=f"{request.correlation_id}:{sequence}:observe")
                    simulation_duration += int((time.perf_counter() - simulation_started) * 1000)
                    last_observation = observation
                    if observation.status == "TERMINATED":
                        break
                    decision_started = time.perf_counter()
                    try:
                        async with asyncio.timeout(request.budgets.decision_timeout_ms / 1000):
                            decision = await policy.decide(observation)
                    except TimeoutError as error:
                        policy_duration += int((time.perf_counter() - decision_started) * 1000)
                        raise PolicyFailure("DECISION_TIMEOUT", "Policy decision exceeded its deadline", retryable=True) from error
                    duration = int((time.perf_counter() - decision_started) * 1000)
                    policy_duration += duration
                    if decision.action.type.value == "RESTART":
                        restarts += 1
                        if restarts > request.budgets.max_restarts:
                            raise PolicyFailure("RESTART_BUDGET_EXHAUSTED", "Restart budget is exhausted")
                    simulation_started = time.perf_counter()
                    transition = await environment.step(decision.action, correlation_id=f"{request.correlation_id}:{sequence}:step")
                    simulation_duration += int((time.perf_counter() - simulation_started) * 1000)
                    transition_data = transition.model_dump(by_alias=True, mode="json")
                    step = {
                        "sequence": sequence, "attempt": observation.model_dump(by_alias=True).get("attempt", 1),
                        "simulationStepBefore": observation.step, "simulationStepAfter": transition.step,
                        "observation": observation.model_dump(by_alias=True, mode="json"),
                        "observationDigest": canonical_digest(observation.model_dump(by_alias=True, mode="json")),
                        "decision": {"requestedAction": decision.action.model_dump(by_alias=True, mode="json"), "policyDurationMs": duration, "modelCallId": decision.model_call_id},
                        "transition": {key: value for key, value in transition_data.items() if key not in ("protocolVersion", "episodeId", "step", "requestedAction", "observation")},
                        "reward": {"version": request.metric_version, "valueMicros": transition_data.get("scoreDelta", 0) * 1_000_000 if transition.accepted else 0},
                    }
                    result["steps"].append(step)
                    if decision.model_call_id:
                        result["audit"]["modelCalls"].append({
                            "modelCallId": decision.model_call_id, "responseDigest": decision.response_digest,
                            "providerLatencyMs": decision.provider_latency_ms, "mock": decision.mock,
                        })
                        result["audit"]["mock"] = result["audit"]["mock"] or decision.mock
                        _merge_usage(result["usage"], decision)
                    last_observation = transition.observation
                    if transition.status == "TERMINATED":
                        result["executionStatus"] = "COMPLETED"
                        result["terminationReason"] = transition.termination_reason
                        result["outcome"] = _outcome(transition.termination_reason)
                        break
                else:
                    raise PolicyFailure("DECISION_BUDGET_EXHAUSTED", "Decision budget is exhausted")
    except asyncio.CancelledError:
        raise
    except PolicyFailure as error:
        result["executionStatus"] = "FAILED"
        result["outcome"] = "ERROR"
        result["error"] = _error("POLICY", error.code, str(error), len(result["steps"]) + 1, error.retryable)
        if isinstance(policy, LlmPlayerPolicy):
            result["audit"]["modelCalls"] = policy.audit_calls
            result["audit"]["mock"] = any(call.get("mock") is True for call in policy.audit_calls)
            if policy.calls and result["usage"]["unavailableReason"] == "NOT_INVOKED":
                result["usage"] = _usage("UNAVAILABLE", "PROVIDER_FAILURE")
    except SimulationClientError as error:
        result["executionStatus"] = "FAILED"
        result["outcome"] = "ERROR"
        result["error"] = _error("DEPENDENCY", error.code, "Simulation environment request failed", len(result["steps"]) + 1, error.retriable)
    except TimeoutError:
        result["executionStatus"] = "FAILED"
        result["outcome"] = "ERROR"
        result["error"] = _error("RUNNER", "WALL_TIMEOUT", "Episode exceeded its wall deadline", len(result["steps"]) + 1, True)
    except Exception:
        result["executionStatus"] = "FAILED"
        result["outcome"] = "ERROR"
        result["error"] = _error("RUNNER", "PLAYER_INTERNAL_ERROR", "Player execution failed safely", len(result["steps"]) + 1)

    result["stepCount"] = len(result["steps"])
    result["acceptedActionCount"] = sum(1 for step in result["steps"] if step["transition"].get("accepted") is True)
    result["invalidActionCount"] = result["stepCount"] - result["acceptedActionCount"]
    if last_observation is not None:
        data = last_observation.model_dump(by_alias=True, mode="json")
        result["finalStateHash"] = last_observation.state_hash
        result["finalScore"] = data.get("progress", {}).get("score", 0)
        result["trajectoryDigest"] = canonical_digest(result["steps"])
    result["timing"] = {
        "queuedMs": 0, "wallDurationMs": int((time.perf_counter() - started) * 1000),
        "simulationDurationMs": simulation_duration, "policyDurationMs": policy_duration,
    }
    return result


def _merge_usage(usage: dict[str, Any], decision: Any) -> None:
    reported = decision.usage or {}
    keys = ("inputTokens", "outputTokens", "totalTokens")
    if all(isinstance(reported.get(key), int) for key in keys):
        if usage["status"] != "REPORTED":
            usage.update(_usage("REPORTED"))
            for key in keys: usage[key] = 0
            usage["providerLatencyMs"] = 0
        for key in keys: usage[key] += reported[key]
        usage["providerLatencyMs"] += decision.provider_latency_ms or 0
    else:
        usage.update(_usage("UNAVAILABLE", "PROVIDER_USAGE_MISSING"))
        usage["providerLatencyMs"] = decision.provider_latency_ms


async def run_episode_batch(requests: list[PlayerEpisodeRequest], concurrency: int, **kwargs: Any) -> list[dict[str, Any]]:
    """Run a batch with caller-bounded concurrency while preserving input order."""
    semaphore = asyncio.Semaphore(concurrency)
    async def execute(item: PlayerEpisodeRequest) -> dict[str, Any]:
        async with semaphore:
            return await run_episode(item, **kwargs)
    return await asyncio.gather(*(execute(item) for item in requests))


async def replay_recorded_decisions(request: PlayerEpisodeRequest, actions: list[Action], **kwargs: Any) -> dict[str, Any]:
    return await run_episode(request, policy=RecordedDecisionPolicy(actions), **kwargs)
