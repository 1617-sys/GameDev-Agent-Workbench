from copy import deepcopy
from typing import Any

from app.schemas.player import (
    ModelRef, Observation, ObservationPolicy, PersonaRef, PlayerBudgets, PlayerEpisodeRequest,
    PolicyRef, PrototypeBinding, SimulationRunInput, StepResult,
)
from app.services.player.policies import DETERMINISTIC_POLICY_DIGEST, LLM_POLICY_DIGEST, PERSONAS, canonical_digest


class GridEnvironment:
    def __init__(self, *, target_x: int = 20, exit_x: int = 40, fail_observe: bool = False) -> None:
        self.x = 0
        self.step_number = 0
        self.collected = False
        self.terminated = False
        self.target_x = target_x
        self.exit_x = exit_x
        self.fail_observe = fail_observe
        self.closed = False
        self.actions: list[str] = []

    async def __aenter__(self): return self
    async def __aexit__(self, *args): self.closed = True

    async def reset(self, request):
        self.request = request
        return self._observation()

    async def observe(self, **kwargs):
        if self.fail_observe:
            from app.clients.simulation_client import SimulationTransportError
            raise SimulationTransportError("ENV_DOWN", "hidden body", retriable=True)
        return self._observation()

    async def step(self, action, **kwargs):
        previous = self._hash()
        name = action.type.value
        self.actions.append(name)
        if name == "MOVE_RIGHT": self.x += 10
        elif name == "MOVE_LEFT": self.x -= 10
        self.step_number += 1
        score_delta = 0
        if not self.collected and abs(self.x - self.target_x) <= 4:
            self.collected = True
            score_delta = 100
        if self.collected and abs(self.x - self.exit_x) <= 4:
            self.terminated = True
            score_delta += 500
        observation = self._observation()
        return StepResult.model_validate({
            "protocolVersion": "simulation/1.0", "episodeId": self.request.episode_id,
            "step": self.step_number, "requestedAction": {"type": name}, "appliedAction": {"type": name},
            "accepted": True, "advanced": True, "previousStateHash": previous, "stateHash": self._hash(),
            "status": "TERMINATED" if self.terminated else "RUNNING",
            "terminationReason": "WON" if self.terminated else None, "scoreDelta": score_delta,
            "events": [], "error": None, "observation": observation.model_dump(by_alias=True, mode="json"),
        })

    def _hash(self): return canonical_digest({"x": self.x, "step": self.step_number, "collected": self.collected, "terminated": self.terminated})

    def _observation(self):
        entities = []
        if not self.collected: entities.append({"type": "collectible", "id": "item", "position": {"x": self.target_x, "y": 0}, "radius": 4})
        entities.append({"type": "exit", "id": "exit", "position": {"x": self.exit_x, "y": 0}, "width": 8, "height": 8})
        policy = getattr(self, "request", None)
        kind = policy.observation_policy.kind if policy else "FULL"
        return Observation.model_validate({
            "protocolVersion": "simulation/1.0", "episodeId": getattr(policy, "episode_id", "episode"),
            "kind": kind, "step": self.step_number, "attempt": 1, "stateHash": self._hash(),
            "status": "TERMINATED" if self.terminated else "RUNNING", "terminationReason": "WON" if self.terminated else None,
            "player": {"position": {"x": self.x, "y": 0}},
            "progress": {"collected": int(self.collected), "target": 1, "score": 600 if self.terminated else 100 if self.collected else 0, "exitUnlocked": self.collected},
            "visibleEntities": entities, "lastAction": {"type": self.actions[-1] if self.actions else None},
        })


def episode_request(*, persona_id="baseline-neutral", kind="DETERMINISTIC", max_decisions=20, policy_seed=1, key="episode-1"):
    persona = PERSONAS[persona_id]
    policy_digest = DETERMINISTIC_POLICY_DIGEST if kind == "DETERMINISTIC" else LLM_POLICY_DIGEST
    observation_policy = ObservationPolicy(kind="FULL") if persona_id == "baseline-neutral" else ObservationPolicy(kind="PERSONA", vision_radius_px=persona.vision_radius_px)
    return PlayerEpisodeRequest(
        episode_id=f"00000000-0000-4000-8000-{1 if key == 'episode-1' else 2:012d}",
        batch_id="00000000-0000-4000-8000-000000000099", client_episode_key=key,
        correlation_id="trace-player-0001",
        prototype=PrototypeBinding(project_uuid="project", prototype_version_uuid="version", game_config_artifact_uuid="artifact", config_digest="a" * 64, runtime_capability_version="arcade/1"),
        simulation=SimulationRunInput(core_version="core/1", seed=42, max_steps=max_decisions, observation_policy=observation_policy),
        policy=PolicyRef(kind=kind, policy_id="deterministic-heuristic" if kind == "DETERMINISTIC" else "llm-step", policy_version="1.0", policy_digest=policy_digest),
        persona=PersonaRef(persona_id=persona_id, persona_version="1.0", persona_digest=persona.digest, policy_seed=policy_seed),
        model=None if kind == "DETERMINISTIC" else ModelRef(provider="openai-compatible", model="test-model"),
        game_config={"metadata": {"schemaVersion": "2.0"}},
        budgets=PlayerBudgets(max_decisions=max_decisions, max_model_calls=max_decisions, wall_timeout_ms=10_000),
    )


def api_payload(request: PlayerEpisodeRequest) -> dict[str, Any]:
    return deepcopy(request.model_dump(by_alias=True, mode="json"))
