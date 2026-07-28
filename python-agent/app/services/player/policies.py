import hashlib
import json
from dataclasses import asdict, dataclass
from typing import Any, Awaitable, Callable, Protocol

from app.schemas.player import Action, Observation


def canonical_digest(value: Any) -> str:
    return hashlib.sha256(json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


@dataclass(frozen=True)
class PersonaParameters:
    persona_id: str
    version: str
    vision_radius_px: int
    decision_interval_steps: int
    action_error_permille: int
    planning_depth: int
    max_policy_retries: int
    remember_targets: bool

    @property
    def digest(self) -> str:
        return canonical_digest(asdict(self))


PERSONAS = {
    "baseline-neutral": PersonaParameters("baseline-neutral", "1.0", 2000, 1, 0, 4, 0, True),
    "NOVICE": PersonaParameters("NOVICE", "1.0", 160, 3, 180, 1, 0, False),
    "REGULAR": PersonaParameters("REGULAR", "1.0", 320, 2, 60, 3, 1, True),
    "EXPERT": PersonaParameters("EXPERT", "1.0", 640, 1, 10, 8, 2, True),
}

DETERMINISTIC_POLICY_SPEC = {
    "policyId": "deterministic-heuristic",
    "policyVersion": "1.0",
    "algorithm": "visible-target-axis-navigation-with-obstacle-detour",
}
DETERMINISTIC_POLICY_DIGEST = canonical_digest(DETERMINISTIC_POLICY_SPEC)
LLM_POLICY_SPEC = {"policyId": "llm-step", "policyVersion": "1.0", "prompt": "player-step/1.0"}
LLM_POLICY_DIGEST = canonical_digest(LLM_POLICY_SPEC)


class PolicyFailure(Exception):
    def __init__(self, code: str, message: str, *, retryable: bool = False) -> None:
        super().__init__(message)
        self.code = code
        self.retryable = retryable


@dataclass
class PolicyDecision:
    action: Action
    model_call_id: str | None = None
    usage: dict[str, Any] | None = None
    provider_latency_ms: int | None = None
    response_digest: str | None = None
    mock: bool = False


class PlayerPolicy(Protocol):
    async def decide(self, observation: Observation) -> PolicyDecision: ...


def _point(value: Any) -> tuple[float, float] | None:
    if not isinstance(value, dict):
        return None
    position = value.get("position", value)
    if not isinstance(position, dict):
        return None
    x, y = position.get("x"), position.get("y")
    return (float(x), float(y)) if isinstance(x, (int, float)) and isinstance(y, (int, float)) else None


class DeterministicPlayerPolicy:
    def __init__(self, persona: PersonaParameters, policy_seed: int) -> None:
        self.persona = persona
        self.policy_seed = policy_seed
        self._decision_count = 0
        self._last_action = Action(type="WAIT")
        self._memory: dict[str, dict[str, Any]] = {}

    async def decide(self, observation: Observation) -> PolicyDecision:
        self._decision_count += 1
        data = observation.model_dump(by_alias=True, mode="json")
        visible = data.get("visibleEntities", [])
        for entity in visible:
            if isinstance(entity, dict) and isinstance(entity.get("id"), str):
                self._memory[entity["id"]] = entity
        if self._decision_count > 1 and (self._decision_count - 1) % self.persona.decision_interval_steps:
            return PolicyDecision(self._last_action)

        player = _point(data.get("player"))
        visible_ids = {item.get("id") for item in visible if isinstance(item, dict)}
        if player is not None:
            for target_id, target in list(self._memory.items()):
                point = _point(target)
                if target.get("type") == "collectible" and target_id not in visible_ids and point and abs(point[0] - player[0]) + abs(point[1] - player[1]) <= 30:
                    self._memory.pop(target_id)
        progress = data.get("progress", {})
        exit_unlocked = isinstance(progress, dict) and progress.get("exitUnlocked") is True
        candidates = self._targets(visible, exit_unlocked)
        action = self._navigate(player, candidates, visible)
        action = self._apply_error(action)
        self._last_action = action
        return PolicyDecision(action)

    def _targets(self, visible: list[Any], exit_unlocked: bool) -> list[dict[str, Any]]:
        source = list(visible)
        if self.persona.remember_targets:
            known_ids = {item.get("id") for item in source if isinstance(item, dict)}
            source.extend(item for key, item in self._memory.items() if key not in known_ids)
        wanted = "exit" if exit_unlocked else "collectible"
        return [item for item in source if isinstance(item, dict) and item.get("type") == wanted and _point(item)]

    def _navigate(self, player: tuple[float, float] | None, targets: list[dict[str, Any]], visible: list[Any]) -> Action:
        if player is None or not targets:
            return Action(type="WAIT")
        considered = targets[:self.persona.planning_depth]
        target = min(considered, key=lambda item: abs(_point(item)[0] - player[0]) + abs(_point(item)[1] - player[1]))
        tx, ty = _point(target)
        dx, dy = tx - player[0], ty - player[1]
        horizontal = "MOVE_RIGHT" if dx > 0 else "MOVE_LEFT"
        vertical = "MOVE_DOWN" if dy > 0 else "MOVE_UP"
        primary, alternate = (horizontal, vertical) if abs(dx) >= abs(dy) else (vertical, horizontal)
        if self._blocked(primary, player, visible):
            primary = alternate
        return Action(type=primary)

    @staticmethod
    def _blocked(action: str, player: tuple[float, float], visible: list[Any]) -> bool:
        for entity in visible:
            if not isinstance(entity, dict) or entity.get("type") != "obstacle":
                continue
            point = _point(entity)
            if point is None:
                continue
            x, y = point
            width, height = float(entity.get("width", 0)), float(entity.get("height", 0))
            if action == "MOVE_RIGHT" and 0 <= x - player[0] <= width / 2 + 24 and abs(y - player[1]) <= height / 2 + 20:
                return True
            if action == "MOVE_LEFT" and 0 <= player[0] - x <= width / 2 + 24 and abs(y - player[1]) <= height / 2 + 20:
                return True
            if action == "MOVE_DOWN" and 0 <= y - player[1] <= height / 2 + 24 and abs(x - player[0]) <= width / 2 + 20:
                return True
            if action == "MOVE_UP" and 0 <= player[1] - y <= height / 2 + 24 and abs(x - player[0]) <= width / 2 + 20:
                return True
        return False

    def _apply_error(self, action: Action) -> Action:
        digest = hashlib.sha256(f"{self.policy_seed}:{self._decision_count}".encode()).digest()
        if int.from_bytes(digest[:4], "big") % 1000 >= self.persona.action_error_permille:
            return action
        alternatives = ["WAIT", "MOVE_UP", "MOVE_DOWN", "MOVE_LEFT", "MOVE_RIGHT"]
        alternatives.remove(action.type.value)
        return Action(type=alternatives[int.from_bytes(digest[4:8], "big") % len(alternatives)])


class LlmPlayerPolicy:
    def __init__(
        self,
        decide_call: Callable[[dict[str, Any]], Awaitable[dict[str, Any]]],
        persona: PersonaParameters,
        max_model_calls: int,
    ) -> None:
        self._decide_call = decide_call
        self._persona = persona
        self._max_model_calls = max_model_calls
        self.calls = 0
        self.audit_calls: list[dict[str, Any]] = []

    async def decide(self, observation: Observation) -> PolicyDecision:
        last_error: Exception | None = None
        failure_code = "INVALID_POLICY_OUTPUT"
        for _ in range(self._persona.max_policy_retries + 1):
            if self.calls >= self._max_model_calls:
                raise PolicyFailure("MODEL_BUDGET_EXHAUSTED", "Model call budget is exhausted")
            self.calls += 1
            try:
                result = await self._decide_call(observation.model_dump(by_alias=True, mode="json"))
                audit = {
                    "modelCallId": result.get("modelCallId"), "responseDigest": result.get("responseDigest"),
                    "providerLatencyMs": result.get("providerLatencyMs"), "mock": result.get("mock") is True,
                    "status": "PARSED",
                }
                self.audit_calls.append(audit)
                action = Action.model_validate(result.get("action"))
                return PolicyDecision(
                    action=action, model_call_id=result.get("modelCallId"), usage=result.get("usage"),
                    provider_latency_ms=result.get("providerLatencyMs"), response_digest=result.get("responseDigest"),
                    mock=result.get("mock") is True,
                )
            except PolicyFailure:
                raise
            except TimeoutError as error:
                failure_code = "MODEL_TIMEOUT"
                last_error = error
                self.audit_calls.append({"modelCallId": None, "responseDigest": None, "providerLatencyMs": None, "mock": False, "status": "MODEL_TIMEOUT"})
            except Exception as error:
                last_error = error
                if self.audit_calls and self.audit_calls[-1]["status"] == "PARSED":
                    self.audit_calls[-1]["status"] = "PARSE_ERROR"
        message = "Model provider timed out" if failure_code == "MODEL_TIMEOUT" else "Model returned an invalid single Action"
        raise PolicyFailure(failure_code, message, retryable=failure_code == "MODEL_TIMEOUT") from last_error


class RecordedDecisionPolicy:
    def __init__(self, actions: list[Action]) -> None:
        self._actions = iter(actions)

    async def decide(self, observation: Observation) -> PolicyDecision:
        del observation
        try:
            return PolicyDecision(next(self._actions))
        except StopIteration as error:
            raise PolicyFailure("RECORDED_DECISIONS_EXHAUSTED", "Recorded decision sequence ended before the environment") from error
