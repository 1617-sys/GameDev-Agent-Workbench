import logging
import os
import time
import json
import hashlib
from uuid import uuid4
from dataclasses import dataclass
from typing import Any

import httpx

from app.prompts.player import build_player_messages

logger = logging.getLogger("python-agent.llm")


@dataclass
class LLMResult:
    content: str
    model: str
    time_taken_ms: int
    raw_response: dict[str, Any] | None = None
    success: bool = True
    fallback_used: bool = False
    error_type: str | None = None
    error_message: str | None = None


class LLMClient:
    def __init__(self) -> None:
        self.base_url = os.getenv("LLM_BASE_URL", "").rstrip("/")
        self.api_key = os.getenv("LLM_API_KEY", "")
        self.model = os.getenv("LLM_MODEL", "deepseek-chat")
        self.timeout_seconds = float(os.getenv("LLM_TIMEOUT_SECONDS", "30"))
        self.enable_mock_fallback = os.getenv("LLM_ENABLE_MOCK_FALLBACK", "false").lower() == "true"

    def generate(self, system_prompt: str, user_prompt: str) -> LLMResult:
        start_time = time.perf_counter()

        if not self.base_url or not self.api_key:
            error_message = "LLM_BASE_URL or LLM_API_KEY is missing"
            logger.warning("LLM config missing model=%s fallbackEnabled=%s", self.model, self.enable_mock_fallback)
            if self.enable_mock_fallback:
                return self._mock_result(start_time, user_prompt, "LLMConfigError", error_message)
            raise RuntimeError(error_message)

        url = f"{self.base_url}/v1/chat/completions"
        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json",
        }
        body = {
            "model": self.model,
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ],
            "temperature": 0.7,
        }

        try:
            logger.info("LLM request started model=%s url=%s", self.model, url)
            with httpx.Client(timeout=self.timeout_seconds) as client:
                response = client.post(url, headers=headers, json=body)
                response.raise_for_status()

            data = response.json()
            content = data["choices"][0]["message"]["content"]
            time_taken_ms = int((time.perf_counter() - start_time) * 1000)
            logger.info("LLM request succeeded model=%s timeTakenMs=%s", self.model, time_taken_ms)

            return LLMResult(
                content=content,
                model=self.model,
                time_taken_ms=time_taken_ms,
                raw_response=data,
            )
        except Exception as exception:
            time_taken_ms = int((time.perf_counter() - start_time) * 1000)
            error_type = type(exception).__name__
            error_message = str(exception)

            if isinstance(exception, httpx.HTTPStatusError) and exception.response.text:
                error_message = exception.response.text

            logger.warning("LLM request failed model=%s timeTakenMs=%s errorType=%s",
                           self.model, time_taken_ms, error_type)

            if self.enable_mock_fallback:
                return self._mock_result(start_time, user_prompt, error_type, error_message)

            raise

    def _mock_result(
        self,
        start_time: float,
        user_prompt: str,
        error_type: str | None = None,
        error_message: str | None = None,
    ) -> LLMResult:
        return LLMResult(
            content="Mock fallback result.",
            model="mock",
            time_taken_ms=int((time.perf_counter() - start_time) * 1000),
            success=False,
            fallback_used=True,
            error_type=error_type,
            error_message=error_message,
        )

    async def decide_player_action(self, observation: dict[str, Any]) -> dict[str, Any]:
        """Call the configured provider for exactly one structured player action."""
        if not self.base_url or not self.api_key:
            raise RuntimeError("Player LLM credentials are not configured")
        started = time.perf_counter()
        body = {
            "model": self.model,
            "messages": build_player_messages(observation),
            "temperature": 0,
            "response_format": {"type": "json_object"},
        }
        try:
            async with httpx.AsyncClient(timeout=self.timeout_seconds) as client:
                response = await client.post(
                    f"{self.base_url}/v1/chat/completions",
                    headers={"Authorization": f"Bearer {self.api_key}", "Content-Type": "application/json"},
                    json=body,
                )
                response.raise_for_status()
            payload = response.json()
            content = payload["choices"][0]["message"]["content"]
            parsed = json.loads(content)
            usage = payload.get("usage") or {}
            latency = int((time.perf_counter() - started) * 1000)
            return {
                "action": parsed.get("action"),
                "modelCallId": str(payload.get("id") or uuid4()),
                "usage": {
                    "inputTokens": usage.get("prompt_tokens"),
                    "outputTokens": usage.get("completion_tokens"),
                    "totalTokens": usage.get("total_tokens"),
                },
                "providerLatencyMs": latency,
                "responseDigest": hashlib.sha256(content.encode()).hexdigest(),
                "mock": False,
            }
        except (httpx.TimeoutException, TimeoutError) as error:
            raise TimeoutError("Player model request timed out") from error
        except httpx.HTTPError as error:
            raise RuntimeError("Player model provider request failed") from error
