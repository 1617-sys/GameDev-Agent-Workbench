from __future__ import annotations

from enum import Enum
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator


def _camel(name: str) -> str:
    parts = name.split("_")
    return parts[0] + "".join(part.capitalize() for part in parts[1:])


class DirectorModel(BaseModel):
    model_config = ConfigDict(alias_generator=_camel, populate_by_name=True, extra="forbid")


class DecisionKind(str, Enum):
    CALL_TOOL = "CALL_TOOL"
    REQUEST_APPROVAL = "REQUEST_APPROVAL"
    FINISH = "FINISH"
    FAIL = "FAIL"


class Budget(DirectorModel):
    max_rounds: int = Field(ge=1, le=100)
    max_tool_calls: int = Field(ge=0, le=100)
    max_candidates: int = Field(ge=0, le=1000)
    max_episodes: int = Field(ge=0, le=100_000)
    max_tokens: int = Field(ge=0, le=10_000_000)
    max_cost_micros: int = Field(ge=0)
    max_wall_clock_ms: int = Field(ge=1, le=86_400_000)
    max_failures: int = Field(ge=0, le=100)
    decision_timeout_ms: int = Field(default=30_000, ge=10, le=120_000)


class BudgetUsage(DirectorModel):
    rounds: int = Field(default=0, ge=0)
    tool_calls: int = Field(default=0, ge=0)
    candidates: int = Field(default=0, ge=0)
    episodes: int = Field(default=0, ge=0)
    tokens: int = Field(default=0, ge=0)
    cost_micros: int = Field(default=0, ge=0)
    wall_clock_ms: int = Field(default=0, ge=0)
    failures: int = Field(default=0, ge=0)


class MetricInterval(DirectorModel):
    minimum: float | None = Field(default=None, alias="min")
    maximum: float | None = Field(default=None, alias="max")

    @model_validator(mode="after")
    def valid_range(self):
        if self.minimum is None and self.maximum is None:
            raise ValueError("metric target requires min or max")
        if self.minimum is not None and self.maximum is not None and self.minimum > self.maximum:
            raise ValueError("metric min cannot exceed max")
        return self


class MetricTarget(DirectorModel):
    name: str = Field(pattern=r"^[A-Z][A-Z0-9_]{2,79}$")
    target: MetricInterval


class Guardrail(DirectorModel):
    name: str = Field(pattern=r"^[A-Z][A-Z0-9_]{2,79}$")
    operator: Literal["LT", "LTE", "EQ", "GTE", "GT"]
    value: float


class AllowedParameter(DirectorModel):
    path: str = Field(pattern=r"^[A-Za-z][A-Za-z0-9_.]{1,119}$")
    minimum: float = Field(alias="min")
    maximum: float = Field(alias="max")

    @model_validator(mode="after")
    def valid_range(self):
        if self.minimum > self.maximum:
            raise ValueError("parameter min cannot exceed max")
        return self


class DesignGoal(DirectorModel):
    protocol_version: Literal["director/1.0"] = "director/1.0"
    source_text_digest: str = Field(pattern=r"^[0-9a-f]{64}$")
    metrics: list[MetricTarget] = Field(min_length=1, max_length=20)
    guardrails: list[Guardrail] = Field(default_factory=list, max_length=20)
    allowed_parameters: list[AllowedParameter] = Field(default_factory=list, max_length=50)
    budget: Budget


class ToolDefinition(DirectorModel):
    name: str = Field(pattern=r"^[A-Z][A-Z0-9_]{2,79}$")
    version: str = Field(pattern=r"^[0-9]+$")
    argument_schema: dict[str, Any]
    permission: Literal["READ", "WRITE"]
    risk_level: Literal["LOW", "MEDIUM", "HIGH"]
    timeout_ms: int = Field(ge=10, le=120_000)


class ToolResult(DirectorModel):
    call_id: str = Field(min_length=1, max_length=80)
    tool_name: str
    status: Literal["SUCCEEDED", "FAILED", "TIMED_OUT"]
    output_digest: str = Field(pattern=r"^[0-9a-f]{64}$")
    summary: dict[str, Any] = Field(default_factory=dict)
    result_ref: str | None = Field(default=None, max_length=255)


class DirectorSnapshot(DirectorModel):
    protocol_version: Literal["director/1.0"] = "director/1.0"
    run_id: str = Field(min_length=1, max_length=80)
    project_id: str = Field(min_length=1, max_length=80)
    state_version: int = Field(ge=0)
    status: Literal["PENDING", "RUNNING", "WAITING_EXPERIMENT", "WAITING_APPROVAL"]
    goal: DesignGoal
    usage: BudgetUsage = Field(default_factory=BudgetUsage)
    allowed_tools: list[ToolDefinition] = Field(default_factory=list, max_length=50)
    recent_tool_results: list[ToolResult] = Field(default_factory=list, max_length=20)
    requested_tool: str | None = None
    next_tool_arguments: dict[str, Any] = Field(default_factory=dict)
    approval_required: bool = False
    target_met: bool = False
    terminal_summary: str | None = Field(default=None, max_length=1000)
    model_mode: Literal["AUTO", "TOOL", "APPROVAL", "FINISH", "FAIL"] = "AUTO"


class ToolCall(DirectorModel):
    call_id: str
    tool_name: str
    tool_version: str
    idempotency_key: str
    arguments: dict[str, Any]
    dry_run: bool = False


class ModelEvidence(DirectorModel):
    provider: str
    model: str
    prompt_version: str
    input_digest: str
    output_digest: str
    token_usage: int = 0


class DirectorDecision(DirectorModel):
    protocol_version: Literal["director/1.0"] = "director/1.0"
    run_id: str
    state_version: int
    round: int
    kind: DecisionKind
    reason_summary: str = Field(max_length=500)
    tool_call: ToolCall | None = None
    approval: dict[str, Any] | None = None
    outcome: dict[str, Any] | None = None
    error: dict[str, Any] | None = None
    model_evidence: ModelEvidence
    decision_digest: str

    @model_validator(mode="after")
    def exactly_one_payload(self):
        values = [self.tool_call, self.approval, self.outcome, self.error]
        if sum(value is not None for value in values) != 1:
            raise ValueError("decision must contain exactly one payload")
        expected = {
            DecisionKind.CALL_TOOL: self.tool_call,
            DecisionKind.REQUEST_APPROVAL: self.approval,
            DecisionKind.FINISH: self.outcome,
            DecisionKind.FAIL: self.error,
        }
        if expected[self.kind] is None:
            raise ValueError("decision payload does not match kind")
        return self
