from app.schemas.agent import AgentMockRequest
from app.services.langchain_agent import normalize_game_config


def request() -> AgentMockRequest:
    return AgentMockRequest(title="Demo", content="A small collect game")


def test_normalizes_legacy_aliases_to_frozen_runtime_contract():
    result = normalize_game_config(
        {
            "collectibles": [{"id": "core", "x": 100, "y": 120}],
            "enemies": [{"id": "bot", "x": 300, "y": 200, "patrolDistance": 90}],
            "winCondition": {"collectAll": True, "reachExit": True},
            "ui": {"controlHint": "Use arrows"},
        },
        request(),
    )

    assert "collectibles" not in result
    assert "winCondition" not in result
    assert result["items"][0]["size"] == 18
    assert result["enemies"][0]["range"] == 90
    assert result["enemies"][0]["axis"] == "x"
    assert result["rules"]["targetItems"] == 1
    assert result["ui"]["controls"] == "Use arrows"
    assert result["obstacles"]


def test_defaults_form_a_playable_game_config_v1():
    result = normalize_game_config({}, request())

    assert result["version"] == "1.0"
    assert result["gameType"] == "top_down_collect"
    assert result["world"]["width"] > 0
    assert result["player"]["speed"] > 0
    assert len(result["items"]) == result["rules"]["targetItems"]
    assert isinstance(result["theme"]["palette"], dict)
    assert result["ui"]["controls"]


def test_preserves_agent_authored_layout_and_vertical_patrol():
    result = normalize_game_config(
        {
            "obstacles": [{"id": "case", "x": 320, "y": 240, "width": 140, "height": 36}],
            "enemies": [{"id": "guard", "x": 600, "y": 260, "speed": 70, "range": 120, "axis": "y"}],
        },
        request(),
    )

    assert result["obstacles"] == [{"id": "case", "x": 320, "y": 240, "width": 140, "height": 36}]
    assert result["enemies"][0]["axis"] == "y"
