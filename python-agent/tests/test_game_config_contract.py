import json
from pathlib import Path

import pytest
from pydantic import ValidationError

from app.prompts.agent_prompts import (
    build_game_config_user_prompt,
    canonical_game_config_example,
)
from app.schemas.agent import AgentMockRequest
from app.schemas.game_config import validate_game_config_v2
from app.services.langchain_agent import parse_game_config


ROOT = Path(__file__).resolve().parents[2]
FIXTURES = ROOT / "docs" / "requirements" / "v3" / "examples" / "game-config-2.0"


def load(name: str) -> dict:
    return json.loads((FIXTURES / name).read_text(encoding="utf-8"))


def request() -> AgentMockRequest:
    return AgentMockRequest(title="Demo", content="A small collect game")


def test_packaged_prompt_example_is_mechanically_synced_and_valid():
    documented = load("valid-minimal.json")
    assert canonical_game_config_example() == documented
    assert validate_game_config_v2(documented) == documented
    prompt = build_game_config_user_prompt(request())
    assert '"schemaVersion": "2.0"' in prompt
    assert '"gameType": "arcade_collect"' in prompt
    assert "top_down_collect" not in prompt


def test_parser_accepts_only_direct_valid_v2_json_and_normalizes_colors():
    result = parse_game_config(json.dumps(load("valid-minimal.json"), ensure_ascii=False), request())
    assert result["metadata"]["schemaVersion"] == "2.0"
    assert result["presentation"]["palette"]["floor"] == "#14213D"


@pytest.mark.parametrize(
    "fixture",
    ["invalid-missing-entities.json", "invalid-remote-resource.json", "invalid-out-of-bounds-patrol.json"],
)
def test_documented_invalid_examples_are_rejected(fixture: str):
    with pytest.raises(ValidationError):
        validate_game_config_v2(load(fixture))


def test_rejects_legacy_aliases_unknown_fields_and_default_traps():
    with pytest.raises(ValidationError):
        validate_game_config_v2(load("legacy-valid-1.0.json"))
    incomplete = load("valid-minimal.json")
    del incomplete["entities"]
    with pytest.raises(ValidationError):
        validate_game_config_v2(incomplete)
    unknown = load("valid-minimal.json")
    unknown["rules"] = {"targetItems": 2}
    with pytest.raises(ValidationError):
        validate_game_config_v2(unknown)


def test_rejects_markdown_wrappers_and_numeric_strings():
    raw = json.dumps(load("valid-minimal.json"), ensure_ascii=False)
    with pytest.raises(ValueError, match="direct valid JSON object"):
        parse_game_config(f"```json\n{raw}\n```", request())
    invalid = load("valid-minimal.json")
    invalid["viewport"]["width"] = "960"
    with pytest.raises(ValidationError):
        validate_game_config_v2(invalid)


def test_accepts_closed_interval_boundary_values():
    config = load("valid-minimal.json")
    config["player"]["speed"] = 80
    config["player"]["maxHealth"] = 5
    config["balance"]["timeLimitSeconds"] = 600
    config["balance"]["winBonus"] = 0
    assert validate_game_config_v2(config)["player"]["speed"] == 80


def test_rejects_entity_overlap_with_obstacles():
    config = load("valid-minimal.json")
    config["world"]["spawn"] = {"x": 360, "y": 180}
    with pytest.raises(ValidationError, match="overlaps obstacle"):
        validate_game_config_v2(config)
