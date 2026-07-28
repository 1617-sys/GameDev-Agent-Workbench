import hashlib
import json
from typing import Any

PLAYER_PROMPT_VERSION = "player-step/1.0"
SYSTEM_PROMPT = (
    "You control one arcade player step. Return JSON only: "
    '{"action":{"type":"MOVE_UP|MOVE_DOWN|MOVE_LEFT|MOVE_RIGHT|WAIT|RESTART"}}. '
    "Choose exactly one action from the supplied projected observation; never invent hidden state."
)
PLAYER_PROMPT_DIGEST = hashlib.sha256(SYSTEM_PROMPT.encode()).hexdigest()


def build_player_messages(observation: dict[str, Any]) -> list[dict[str, str]]:
    bounded = {
        key: observation.get(key)
        for key in ("step", "attempt", "elapsedMs", "remainingMs", "status", "terminationReason", "player", "progress", "visibleEntities", "lastAction")
    }
    serialized = json.dumps(bounded, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    if len(serialized) > 24_000:
        serialized = serialized[:24_000]
    return [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": f"Projected observation JSON:\n{serialized}"},
    ]
