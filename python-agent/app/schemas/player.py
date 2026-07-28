from enum import Enum
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field


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
