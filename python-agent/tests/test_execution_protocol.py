from types import SimpleNamespace

from app.routers.agent import _build_response
from app.schemas.agent import AgentMockRequest
from app.services.mock_agent import build_requirement_breakdown_result


def test_mock_response_is_explicit_and_does_not_expose_raw_input_or_prompt():
    result = build_requirement_breakdown_result(AgentMockRequest(title="title", content="private input"))
    response = _build_response(SimpleNamespace(state=SimpleNamespace(trace_id="trace-1")), result)

    assert response.data["status"] == "SUCCESS"
    assert response.data["mock"] is True
    assert response.data["provider"] == "mock"
    assert response.data["output"]["model"] == "mock"
    assert "raw_result" not in response.data["output"]
    assert "prompt" not in response.data["output"]
