"""Deterministic R7 fault fixture controlled only through a container-local file."""

from __future__ import annotations

import hashlib
import json
import time
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path


MODE_FILE = Path("/tmp/r7-fault-mode")
ALLOWED_MODES = {"normal", "429", "invalid", "delay"}

VALID_GAME_CONFIG = {
    "version": "1.0",
    "title": "R7 fault fixture: Crystal Relay",
    "gameType": "top_down_collect",
    "world": {"width": 960, "height": 540, "backgroundColor": "#111827"},
    "player": {"x": 96, "y": 96, "speed": 220, "color": "#60a5fa"},
    "items": [{"id": "relay-core", "x": 260, "y": 140, "label": "Relay Core"}],
    "enemies": [],
    "exit": {"x": 860, "y": 450, "lockedUntilCollected": True},
    "rules": {"winCondition": "collect_all_then_exit"},
    "ui": {"showScore": True},
}


def mode() -> str:
    try:
        value = MODE_FILE.read_text(encoding="utf-8").strip()
    except FileNotFoundError:
        return "normal"
    return value if value in ALLOWED_MODES else "normal"


def selected_references(payload: dict) -> list[dict]:
    rag = payload.get("rag") or {}
    if not rag.get("rag_enabled"):
        return []
    return [
        {
            "chunk_uuid": item.get("chunk_uuid"),
            "document_uuid": item.get("document_uuid"),
            "document_version": item.get("document_version"),
            "rank": item.get("rank"),
            "score": item.get("score"),
        }
        for item in rag.get("retrieved_chunks", [])
        if item.get("chunk_uuid") and item.get("document_uuid")
    ]


def fixture_output(path: str) -> dict:
    if path.endswith("/game-config-generate"):
        return VALID_GAME_CONFIG
    return {
        "fixture": "r7-fault-fixed-agent-v1",
        "agent_path": path.rsplit("/", 1)[-1],
        "summary": "Deterministic fake output for the R7 fault-recovery harness.",
    }


class Handler(BaseHTTPRequestHandler):
    server_version = "R7FaultFixture/1.0"

    def log_message(self, _format: str, *_args: object) -> None:
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
            self._json(HTTPStatus.OK, {"status": "UP", "providerMode": "fake"})
            return
        self._json(HTTPStatus.NOT_FOUND, {"code": 404, "message": "not found"})

    def do_POST(self) -> None:
        if not self.path.startswith("/agent/"):
            self._json(HTTPStatus.NOT_FOUND, {"code": 404, "message": "not found"})
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            payload = json.loads(self.rfile.read(length).decode("utf-8"))
        except (ValueError, UnicodeDecodeError, json.JSONDecodeError):
            self._json(HTTPStatus.BAD_REQUEST, {"code": 400, "message": "invalid JSON"})
            return

        active_mode = mode()
        if active_mode == "delay":
            time.sleep(30)
        elif active_mode == "429":
            self._json(HTTPStatus.TOO_MANY_REQUESTS, {"code": 429, "message": "controlled rate limit"})
            return
        elif active_mode == "invalid":
            encoded = b"{controlled-invalid-json"
            self.send_response(HTTPStatus.OK)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(encoded)))
            self.end_headers()
            self.wfile.write(encoded)
            return

        references = selected_references(payload)
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
                    "model": "r7-fault-fixed-agent-v1",
                    "provider": "fixture",
                    "usage": None,
                    "latency_ms": 5,
                    "mock": True,
                    "rag_status": "AVAILABLE" if references else "DISABLED",
                    "used_references": references,
                },
            },
        )


if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", 8000), Handler).serve_forever()
