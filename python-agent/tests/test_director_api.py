from fastapi.testclient import TestClient

from app.main import app
from tests.test_director_graph import snapshot


client = TestClient(app)


def test_director_endpoint_requires_internal_authentication():
    assert client.post("/director/decisions", json=snapshot()).status_code == 401


def test_director_endpoint_returns_one_structured_decision():
    response = client.post("/director/decisions", json=snapshot(), headers={
        "X-Internal-Token": "test-only-python-agent-token-at-least-32-bytes",
        "X-Trace-Id": "director-test-trace",
    })
    assert response.status_code == 200
    body = response.json()
    assert body["kind"] == "CALL_TOOL"
    assert body["toolCall"]["toolName"] == "GET_PROTOTYPE_VERSION"
    assert "approval" not in body or body["approval"] is None
