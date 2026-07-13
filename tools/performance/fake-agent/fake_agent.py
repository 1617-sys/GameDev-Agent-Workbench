"""Network-isolated 300 ms Agent fixture for the R7 performance gate."""

from __future__ import annotations

import hashlib
import json
import threading
import time
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


FIXTURE_VERSION = "r7-performance-fixed-agent-v1"
FIXED_LATENCY_MS = 300
REQUEST_COUNT = 0
REQUEST_COUNT_LOCK = threading.Lock()

VALID_GAME_CONFIG = {
    "version": "1.0",
    "title": "R7 performance fixture",
    "gameType": "top_down_collect",
    "world": {"width": 960, "height": 540, "backgroundColor": "#111827"},
    "player": {"x": 96, "y": 96, "speed": 220, "color": "#60a5fa"},
    "items": [{"id": "relay-core", "x": 260, "y": 140, "label": "Relay Core"}],
    "enemies": [],
    "exit": {"x": 860, "y": 450, "lockedUntilCollected": True},
    "rules": {"winCondition": "collect_all_then_exit"},
    "ui": {"showScore": True},
}


def fixture_output(path: str) -> dict:
    if path.endswith("/game-config-generate"):
        return VALID_GAME_CONFIG
    return {
        "fixture": FIXTURE_VERSION,
        "agent_path": path.rsplit("/", 1)[-1],
        "summary": "Deterministic fake output for the R7 performance harness.",
    }


class Handler(BaseHTTPRequestHandler):
    server_version = "R7PerformanceFixture/1.0"

    def log_message(self, _format: str, *_args: object) -> None:
        # Request bodies are deliberately never logged.
        return

    def _json(self, status: int, body: dict) -> None:
        encoded = json.dumps(body, separators=(",", ":")).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def do_GET(self) -> None:
        if self.path == "/health":
            self._json(
                HTTPStatus.OK,
                {
                    "status": "UP",
                    "providerMode": "fake",
                    "fixtureVersion": FIXTURE_VERSION,
                    "fixedLatencyMs": FIXED_LATENCY_MS,
                },
            )
            return
        if self.path == "/metrics":
            with REQUEST_COUNT_LOCK:
                request_count = REQUEST_COUNT
            self._json(
                HTTPStatus.OK,
                {
                    "providerMode": "fake",
                    "fixtureVersion": FIXTURE_VERSION,
                    "fixedLatencyMs": FIXED_LATENCY_MS,
                    "requestsTotal": request_count,
                },
            )
            return
        self._json(HTTPStatus.NOT_FOUND, {"code": 404, "message": "not found"})

    def do_POST(self) -> None:
        global REQUEST_COUNT
        if not self.path.startswith("/agent/"):
            self._json(HTTPStatus.NOT_FOUND, {"code": 404, "message": "not found"})
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            payload = json.loads(self.rfile.read(length).decode("utf-8"))
        except (ValueError, UnicodeDecodeError, json.JSONDecodeError):
            self._json(HTTPStatus.BAD_REQUEST, {"code": 400, "message": "invalid JSON"})
            return

        time.sleep(FIXED_LATENCY_MS / 1000)
        with REQUEST_COUNT_LOCK:
            REQUEST_COUNT += 1
        trace_seed = f"{payload.get('project_uuid', '')}|{self.path}".encode("utf-8")
        trace_id = "fixture-" + hashlib.sha256(trace_seed).hexdigest()[:24]
        self._json(
            HTTPStatus.OK,
            {
                "code": 0,
                "message": "success",
                "trace_id": trace_id,
                "data": {
                    "status": "SUCCESS",
                    "output": fixture_output(self.path),
                    "raw_output_ref": None,
                    "model": FIXTURE_VERSION,
                    "provider": "fixture",
                    "usage": None,
                    "latency_ms": FIXED_LATENCY_MS,
                    "mock": True,
                    "rag_status": "DISABLED",
                    "used_references": [],
                },
            },
        )


if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", 8000), Handler).serve_forever()
