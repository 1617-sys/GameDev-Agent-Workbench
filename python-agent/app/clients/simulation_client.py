import asyncio
import os
from types import TracebackType
from typing import Any, TypeVar
from uuid import uuid4

import httpx
from pydantic import BaseModel, ValidationError

from app.schemas.player import (
    Action,
    CloseResponse,
    CreateSessionRequest,
    CreateSessionResponse,
    Observation,
    ObserveResponse,
    StepResponse,
    StepResult,
)


class SimulationClientError(Exception):
    def __init__(self, code: str, message: str, *, retriable: bool = False) -> None:
        super().__init__(message)
        self.code = code
        self.retriable = retriable


class SimulationAuthenticationError(SimulationClientError):
    pass


class SimulationProtocolError(SimulationClientError):
    pass


class SimulationTransportError(SimulationClientError):
    pass


ResponseModel = TypeVar("ResponseModel", bound=BaseModel)


class SimulationEnvironmentClient:
    """One asynchronous, step-at-a-time Simulation Service environment session."""

    def __init__(
        self,
        *,
        base_url: str | None = None,
        internal_token: str | None = None,
        connect_timeout: float = 2.0,
        read_timeout: float = 5.0,
        total_timeout: float = 10.0,
        safe_retries: int = 2,
        client: httpx.AsyncClient | None = None,
    ) -> None:
        self._base_url = (base_url or os.getenv("SIMULATION_SERVICE_BASE_URL", "http://simulation-service:8090")).rstrip("/")
        self._token = internal_token or os.getenv("SIMULATION_SERVICE_INTERNAL_TOKEN", "")
        if len(self._token) < 32:
            raise ValueError("SIMULATION_SERVICE_INTERNAL_TOKEN must be at least 32 characters")
        if total_timeout <= 0 or safe_retries < 0:
            raise ValueError("Timeouts must be positive and safe_retries cannot be negative")
        self._total_timeout = total_timeout
        self._safe_retries = safe_retries
        self._owns_client = client is None
        self._client = client or httpx.AsyncClient(
            timeout=httpx.Timeout(read_timeout, connect=connect_timeout, write=read_timeout, pool=connect_timeout)
        )
        self._session_id: str | None = None
        self._episode_id: str | None = None

    @property
    def session_id(self) -> str | None:
        return self._session_id

    async def __aenter__(self) -> "SimulationEnvironmentClient":
        return self

    async def __aexit__(
        self,
        exc_type: type[BaseException] | None,
        exc: BaseException | None,
        traceback: TracebackType | None,
    ) -> bool:
        close_error: BaseException | None = None
        if self._session_id is not None:
            try:
                await asyncio.shield(self.close(reason="context-exit" if exc is None else "context-error"))
            except BaseException as error:
                close_error = error
        await self.aclose()
        if exc is None and close_error is not None:
            raise close_error
        return False

    async def aclose(self) -> None:
        if self._owns_client:
            await self._client.aclose()

    async def reset(self, request: CreateSessionRequest) -> Observation:
        if self._session_id is not None:
            raise SimulationClientError("SESSION_ALREADY_OPEN", "A session is already open")
        response = await self._request(
            "POST", "/v1/sessions", CreateSessionResponse,
            json=request.model_dump(by_alias=True, mode="json"),
            correlation_id=request.correlation_id,
            episode_id=request.episode_id,
            retry_safe=False,
            expected_status=201,
        )
        if response.episode_id != request.episode_id or response.observation.episode_id != request.episode_id:
            self._session_id = response.session_id
            self._episode_id = request.episode_id
            try:
                await self.close(reason="protocol-mismatch")
            except SimulationClientError:
                pass
            raise SimulationProtocolError("EPISODE_MISMATCH", "Simulation response episode does not match the request")
        self._session_id = response.session_id
        self._episode_id = request.episode_id
        return response.observation

    async def observe(self, *, correlation_id: str | None = None) -> Observation:
        session_id = self._require_session()
        response = await self._request(
            "GET", f"/v1/sessions/{session_id}/observation", ObserveResponse,
            correlation_id=correlation_id or str(uuid4()), episode_id=self._episode_id,
            retry_safe=True, expected_status=200,
        )
        self._validate_session(response.session_id)
        return response.observation

    async def step(self, action: Action, *, correlation_id: str | None = None) -> StepResult:
        session_id = self._require_session()
        decision_id = correlation_id or str(uuid4())
        response = await self._request(
            "POST", f"/v1/sessions/{session_id}/steps", StepResponse,
            json={"correlationId": decision_id, "action": action.model_dump(by_alias=True, mode="json")},
            correlation_id=decision_id, episode_id=self._episode_id,
            retry_safe=False, expected_status=200,
        )
        self._validate_session(response.session_id)
        return response.step_result

    async def close(self, *, reason: str = "client-close", correlation_id: str | None = None) -> None:
        session_id = self._require_session()
        response = await self._request(
            "DELETE", f"/v1/sessions/{session_id}", CloseResponse,
            json={"reason": reason}, correlation_id=correlation_id or str(uuid4()),
            episode_id=self._episode_id, retry_safe=True, expected_status=200,
        )
        self._validate_session(response.session_id)
        self._session_id = None
        self._episode_id = None

    def _require_session(self) -> str:
        if self._session_id is None:
            raise SimulationClientError("SESSION_NOT_OPEN", "No simulation session is open")
        return self._session_id

    def _validate_session(self, session_id: str) -> None:
        if session_id != self._session_id:
            raise SimulationProtocolError("SESSION_MISMATCH", "Simulation response session does not match the request")

    async def _request(
        self,
        method: str,
        path: str,
        response_model: type[ResponseModel],
        *,
        correlation_id: str,
        episode_id: str | None,
        retry_safe: bool,
        expected_status: int,
        json: dict[str, Any] | None = None,
    ) -> ResponseModel:
        attempts = self._safe_retries + 1 if retry_safe else 1
        for attempt in range(attempts):
            try:
                async with asyncio.timeout(self._total_timeout):
                    response = await self._client.request(
                        method, f"{self._base_url}{path}", json=json,
                        headers={
                            "x-internal-token": self._token,
                            "x-correlation-id": correlation_id,
                            **({"x-episode-id": episode_id} if episode_id else {}),
                        },
                    )
            except (TimeoutError, httpx.TimeoutException) as error:
                if attempt + 1 < attempts:
                    continue
                raise SimulationTransportError("SIMULATION_TIMEOUT", "Simulation Service request timed out", retriable=True) from error
            except httpx.RequestError as error:
                if attempt + 1 < attempts:
                    continue
                raise SimulationTransportError("SIMULATION_UNAVAILABLE", "Simulation Service request failed", retriable=True) from error

            if response.status_code == expected_status:
                try:
                    return response_model.model_validate(response.json())
                except (ValueError, ValidationError) as error:
                    raise SimulationProtocolError("INVALID_SIMULATION_RESPONSE", "Simulation Service returned an invalid response") from error

            code, message, retriable = self._safe_error(response)
            if retry_safe and retriable and attempt + 1 < attempts:
                continue
            if response.status_code in (401, 403):
                raise SimulationAuthenticationError(code, "Simulation Service authentication failed")
            raise SimulationClientError(code, message, retriable=retriable)
        raise AssertionError("unreachable")

    @staticmethod
    def _safe_error(response: httpx.Response) -> tuple[str, str, bool]:
        default_code = f"SIMULATION_HTTP_{response.status_code}"
        try:
            payload = response.json()
            error = payload.get("error", {}) if isinstance(payload, dict) else {}
            code = error.get("code") if isinstance(error.get("code"), str) else default_code
            retriable = error.get("retriable") is True or response.status_code >= 500
        except ValueError:
            code, retriable = default_code, response.status_code >= 500
        return code, "Simulation Service rejected the request", retriable
