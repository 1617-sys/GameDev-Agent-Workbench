import json
import asyncio
import pytest
from app.schemas.agent import AgentMockRequest
from app.services.balance_evaluation import run_balance_evaluation, validated_inputs
from app.prompts.agent_prompts import canonical_game_config_example

def payload(sample_size=5, extra=None):
    metrics={"prototypeVersionUuid":"version-1","sampleSize":sample_size,"sufficientForAi":sample_size>=5,"winRate":0.4,
        "averageDurationMs":45000,"averageScore":300,"averageHitCount":2.0,"averageCollectedCount":3.0,
        "averageRestartCount":0.5,"failures":{"HEALTH_DEPLETED":2,"TIME_EXPIRED":1,"ABANDONED":0},"snapshotAt":"2026-07-15T00:00:00"}
    if extra: metrics.update(extra)
    return AgentMockRequest(title="balance",content=json.dumps(canonical_game_config_example()),context=json.dumps(metrics))

def test_rejects_small_samples_and_unknown_metrics():
    with pytest.raises(ValueError,match="five"): validated_inputs(payload(4))
    with pytest.raises(ValueError,match="unknown"): validated_inputs(payload(extra={"rawSessions":[]}))

def test_mock_suggestion_is_traceable_and_cautious(monkeypatch):
    monkeypatch.setenv("AGENT_MOCK_MODE","true")
    result=asyncio.run(run_balance_evaluation(payload()))
    assert result.agent_type=="BALANCE_EVALUATION"
    assert "5 个已结束会话" in result.content
    assert "不覆盖当前版本" in result.content
    assert "prompt" not in result.raw_result
