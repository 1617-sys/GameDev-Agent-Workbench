from enum import Enum
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator


class PlayerModel(BaseModel):
    model_config = ConfigDict(alias_generator=lambda name: "".join(
        word if index == 0 else word.capitalize()
        for index, word in enumerate(name.split("_"))
    ), populate_by_name=True, extra="forbid")


class ActionType(str, Enum):
    MOVE_UP = "MOVE_UP"
    MOVE_DOWN = "MOVE_DOWN"
    MOVE_LEFT = "MOVE_LEFT"
    MOVE_RIGHT = "MOVE_RIGHT"
    WAIT = "WAIT"
    RESTART = "RESTART"


class Action(PlayerModel):
    type: ActionType


class ObservationPolicy(PlayerModel):
    kind: Literal["FULL", "PERSONA"]
    vision_radius_px: int | None = Field(default=None, ge=1, le=2000)

    @model_validator(mode="after")
    def valid_projection(self):
        if self.kind == "FULL" and self.vision_radius_px is not None:
            raise ValueError("FULL observation cannot define visionRadiusPx")
        if self.kind == "PERSONA" and self.vision_radius_px is None:
            raise ValueError("PERSONA observation requires visionRadiusPx")
        return self


class CreateSessionRequest(PlayerModel):
    protocol_version: Literal["simulation/1.0"] = "simulation/1.0"
    episode_id: str = Field(min_length=1, max_length=128)
    correlation_id: str = Field(min_length=1, max_length=128)
    config_digest: str = Field(pattern=r"^[0-9a-f]{64}$")
    seed: int = Field(ge=0, le=0xFFFFFFFF)
    max_steps: int = Field(ge=1, le=1_000_000)
    observation_policy: ObservationPolicy
    game_config: dict[str, Any]


class Observation(PlayerModel):
    model_config = ConfigDict(alias_generator=PlayerModel.model_config["alias_generator"], populate_by_name=True, extra="allow")

    protocol_version: Literal["simulation/1.0"]
    episode_id: str
    step: int = Field(ge=0)
    state_hash: str = Field(pattern=r"^[0-9a-f]{64}$")
    status: Literal["RUNNING", "TERMINATED"]
    termination_reason: str | None = None


class CreateSessionResponse(PlayerModel):
    protocol_version: Literal["simulation/1.0"]
    session_id: str = Field(min_length=1, max_length=128)
    episode_id: str
    expires_at: int
    observation: Observation


class ObserveResponse(PlayerModel):
    protocol_version: Literal["simulation/1.0"]
    session_id: str
    observation: Observation


class StepResult(PlayerModel):
    model_config = ConfigDict(alias_generator=PlayerModel.model_config["alias_generator"], populate_by_name=True, extra="allow")

    protocol_version: Literal["simulation/1.0"]
    episode_id: str
    step: int = Field(ge=0)
    accepted: bool
    advanced: bool
    previous_state_hash: str = Field(pattern=r"^[0-9a-f]{64}$")
    state_hash: str = Field(pattern=r"^[0-9a-f]{64}$")
    status: Literal["RUNNING", "TERMINATED"]
    termination_reason: str | None = None
    observation: Observation


class StepResponse(PlayerModel):
    protocol_version: Literal["simulation/1.0"]
    session_id: str
    step_result: StepResult


class CloseResponse(PlayerModel):
    protocol_version: Literal["simulation-service/1.0"]
    session_id: str
    closed: Literal[True]


class PrototypeBinding(PlayerModel):
    project_uuid: str = Field(min_length=1, max_length=80)
    prototype_version_uuid: str = Field(min_length=1, max_length=80)
    game_config_artifact_uuid: str = Field(min_length=1, max_length=80)
    config_digest: str = Field(pattern=r"^[0-9a-f]{64}$")
    game_config_schema_version: Literal["game-config/2.0"] = "game-config/2.0"
    runtime_capability_version: str = Field(min_length=1, max_length=80)


class PolicyRef(PlayerModel):
    kind: Literal["DETERMINISTIC", "LLM"]
    policy_id: str = Field(min_length=1, max_length=80)
    policy_version: str = Field(min_length=1, max_length=40)
    policy_digest: str = Field(pattern=r"^[0-9a-f]{64}$")


class PersonaRef(PlayerModel):
    persona_id: str = Field(min_length=1, max_length=80)
    persona_version: str = Field(min_length=1, max_length=40)
    persona_digest: str = Field(pattern=r"^[0-9a-f]{64}$")
    policy_seed: int = Field(default=0, ge=0, le=0xFFFFFFFF)


class ModelRef(PlayerModel):
    provider: Literal["openai-compatible", "mock"]
    model: str = Field(min_length=1, max_length=100)
    model_version: str | None = Field(default=None, max_length=80)
    prompt_template_id: Literal["player-step"] = "player-step"
    prompt_version: Literal["1.0"] = "1.0"


class SimulationRunInput(PlayerModel):
    protocol_version: Literal["simulation/1.0"] = "simulation/1.0"
    core_version: str = Field(min_length=1, max_length=80)
    seed: int = Field(ge=0, le=0xFFFFFFFF)
    max_steps: int = Field(ge=1, le=100_000)
    observation_policy: ObservationPolicy

    @field_validator("core_version")
    @classmethod
    def immutable_core_version(cls, value: str) -> str:
        if value == "latest":
            raise ValueError("coreVersion must be immutable")
        return value


class PlayerBudgets(PlayerModel):
    max_decisions: int = Field(default=10_000, ge=1, le=100_000)
    max_model_calls: int = Field(default=1_000, ge=0, le=10_000)
    max_restarts: int = Field(default=0, ge=0, le=10)
    decision_timeout_ms: int = Field(default=5_000, ge=10, le=30_000)
    wall_timeout_ms: int = Field(default=120_000, ge=100, le=600_000)


class PlayerEpisodeRequest(PlayerModel):
    episode_protocol_version: Literal["episode/1.0"] = "episode/1.0"
    episode_id: str = Field(min_length=1, max_length=80)
    batch_id: str = Field(min_length=1, max_length=80)
    client_episode_key: str = Field(min_length=1, max_length=80, pattern=r"^[A-Za-z0-9._:-]+$")
    correlation_id: str = Field(min_length=8, max_length=64, pattern=r"^[A-Za-z0-9._:-]+$")
    prototype: PrototypeBinding
    simulation: SimulationRunInput
    policy: PolicyRef
    persona: PersonaRef
    model: ModelRef | None = None
    metric_version: Literal["score-delta/1.0"] = "score-delta/1.0"
    game_config: dict[str, Any]
    budgets: PlayerBudgets = Field(default_factory=PlayerBudgets)

    @model_validator(mode="after")
    def consistent_refs(self):
        if self.prototype.config_digest == "0" * 64:
            raise ValueError("configDigest cannot be the empty digest")
        if self.policy.kind == "DETERMINISTIC" and self.model is not None:
            raise ValueError("deterministic policy cannot select a model")
        if self.policy.kind == "LLM" and self.model is None:
            raise ValueError("LLM policy requires a model")
        if self.budgets.max_decisions > self.simulation.max_steps:
            raise ValueError("maxDecisions cannot exceed maxSteps")
        return self


class PlayerEpisodeBatchRequest(PlayerModel):
    episode_protocol_version: Literal["episode/1.0"] = "episode/1.0"
    client_batch_key: str = Field(min_length=1, max_length=80, pattern=r"^[A-Za-z0-9._:-]+$")
    concurrency: int = Field(default=1, ge=1, le=8)
    episodes: list[PlayerEpisodeRequest] = Field(min_length=1, max_length=100)

    @model_validator(mode="after")
    def unique_items(self):
        keys = [item.client_episode_key for item in self.episodes]
        if len(keys) != len(set(keys)):
            raise ValueError("clientEpisodeKey must be unique within a batch")
        if any(item.batch_id != self.episodes[0].batch_id for item in self.episodes):
            raise ValueError("all episodes must use the same batchId")
        return self
