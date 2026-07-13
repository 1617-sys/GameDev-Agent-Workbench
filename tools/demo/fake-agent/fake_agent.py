"""Explicitly labelled, deterministic offline Provider for the R7 demo only."""

from __future__ import annotations

import hashlib
import hmac
import json
import os
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


FIXTURE_VERSION = "r7-demo-fixture-v1"
GAME_CONFIG = {
    "version": "1.0",
    "title": "DEMO / MOCK: Crystal Relay",
    "gameType": "top_down_collect",
    "world": {"width": 960, "height": 540, "backgroundColor": "#111827"},
    "player": {"x": 96, "y": 96, "speed": 220, "color": "#60a5fa"},
    "items": [
        {"id": "relay-core", "x": 260, "y": 140, "label": "Relay Core"},
        {"id": "signal-key", "x": 520, "y": 300, "label": "Signal Key"},
    ],
    "enemies": [
        {"id": "patrol-1", "x": 420, "y": 220, "speed": 90, "patrolAxis": "x", "patrolDistance": 180}
    ],
    "exit": {"x": 860, "y": 450, "lockedUntilCollected": True},
    "rules": {"winCondition": "collect_all_then_exit"},
    "ui": {"showScore": True},
}


def references(payload: dict) -> list[dict]:
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


class Handler(BaseHTTPRequestHandler):
    server_version = "R7DemoOfflineProvider/1.0"

    def log_message(self, _format: str, *_args: object) -> None:
        return

    def send_json(self, status: int, body: dict) -> None:
        encoded = json.dumps(body, separators=(",", ":")).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def do_GET(self) -> None:
        if self.path == "/health":
            self.send_json(HTTPStatus.OK, {"status": "UP", "providerMode": "offline-mock", "fixture": FIXTURE_VERSION})
            return
        self.send_json(HTTPStatus.NOT_FOUND, {"code": 404, "message": "not found"})

    def do_POST(self) -> None:
        expected = os.getenv("PYTHON_AGENT_INTERNAL_TOKEN", "")
        supplied = self.headers.get("X-Internal-Token", "")
        if not expected or not hmac.compare_digest(expected, supplied):
            self.send_json(HTTPStatus.UNAUTHORIZED, {"code": 401, "message": "internal authentication required"})
            return
        if not self.path.startswith("/agent/"):
            self.send_json(HTTPStatus.NOT_FOUND, {"code": 404, "message": "not found"})
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            payload = json.loads(self.rfile.read(length).decode("utf-8"))
        except (ValueError, UnicodeDecodeError, json.JSONDecodeError):
            self.send_json(HTTPStatus.BAD_REQUEST, {"code": 400, "message": "invalid JSON"})
            return
        used = references(payload)
        trace_seed = f"{payload.get('project_uuid', '')}|{self.path}|{FIXTURE_VERSION}".encode()
        output = GAME_CONFIG if self.path.endswith("/game-config-generate") else {
            "fixture": FIXTURE_VERSION,
            "providerMode": "DEMO / MOCK",
            "agent": self.path.rsplit("/", 1)[-1],
            "summary": "Deterministic offline demo output; this is not a real model result.",
        }
        self.send_json(HTTPStatus.OK, {
            "code": 0,
            "message": "success",
            "trace_id": "demo-" + hashlib.sha256(trace_seed).hexdigest()[:24],
            "data": {
                "status": "SUCCESS",
                "output": output,
                "raw_output_ref": None,
                "model": FIXTURE_VERSION,
                "provider": "demo-offline-fixture",
                "usage": None,
                "latency_ms": 5,
                "mock": True,
                "rag_status": "DISABLED" if not (payload.get("rag") or {}).get("rag_enabled") else ("AVAILABLE" if used else "EMPTY"),
                "used_references": used,
            },
        })


if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", 8000), Handler).serve_forever()
