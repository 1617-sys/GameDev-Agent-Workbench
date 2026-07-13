from fastapi.testclient import TestClient
import logging

from app.main import app


client = TestClient(app)


def test_health_probes_propagate_safe_trace_without_configuration_details():
    for path in ("/health", "/health/live", "/health/ready"):
        response = client.get(path, headers={"X-Trace-Id": "trace-python-1234"})
        assert response.status_code == 200
        assert response.headers["X-Trace-Id"] == "trace-python-1234"
        assert response.json()["trace_id"] == "trace-python-1234"
        serialized = response.text.lower()
        assert "password" not in serialized
        assert "token" not in serialized
        assert "api_key" not in serialized


def test_invalid_trace_header_is_replaced_not_reflected():
    response = client.get("/health", headers={"X-Trace-Id": "bad trace secret=value"})
    assert response.status_code == 200
    assert response.headers["X-Trace-Id"] != "bad trace secret=value"
    assert len(response.headers["X-Trace-Id"]) == 32


def test_agent_logs_record_shape_but_not_payload(caplog):
    caplog.set_level(logging.INFO)
    private_text = "PRIVATE-PROMPT-CONTENT-DO-NOT-LOG"
    response = client.post(
        "/agent/requirement-breakdown",
        headers={"X-Trace-Id": "trace-redaction-1234"},
        json={"title": private_text, "content": private_text},
    )

    assert response.status_code == 200
    rendered = "\n".join(record.getMessage() for record in caplog.records)
    assert private_text not in rendered
    assert f"content_len={len(private_text)}" in rendered
