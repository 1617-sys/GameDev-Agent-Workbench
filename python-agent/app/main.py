import logging
import os
import re
import secrets
import time
from contextvars import ContextVar
from uuid import uuid4

from dotenv import load_dotenv
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

load_dotenv()

from app.routers.agent import router as agent_router
from app.routers.player import router as player_router

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)s | %(name)s | %(message)s",
)

logger = logging.getLogger("python-agent")
trace_context: ContextVar[str] = ContextVar("trace_id", default="none")
safe_trace_id = re.compile(r"^[A-Za-z0-9._:-]{8,64}$")


class TraceContextFilter(logging.Filter):
    def filter(self, record: logging.LogRecord) -> bool:
        record.trace_id = trace_context.get()
        return True


for handler in logging.getLogger().handlers:
    handler.addFilter(TraceContextFilter())
    handler.setFormatter(logging.Formatter(
        "%(asctime)s level=%(levelname)s service=python-agent traceId=%(trace_id)s logger=%(name)s msg=%(message)s"
    ))

app = FastAPI(title="GameDev Agent Mock API", version="0.1.0")
app.include_router(agent_router)
app.include_router(player_router)


@app.middleware("http")
async def authenticate_internal_requests(request: Request, call_next):
    if request.url.path.startswith(("/agent/", "/player/")):
        expected = os.getenv("PYTHON_AGENT_INTERNAL_TOKEN", "")
        supplied = request.headers.get("X-Internal-Token", "")
        if len(expected) < 32 or not secrets.compare_digest(supplied, expected):
            return JSONResponse(status_code=401, content={"code": 401, "message": "unauthorized", "data": None})
    return await call_next(request)


@app.middleware("http")
async def limit_player_request_body(request: Request, call_next):
    if request.url.path.startswith("/player/"):
        content_length = request.headers.get("content-length")
        try:
            declared_too_large = content_length is not None and int(content_length) > 2 * 1024 * 1024
        except ValueError:
            declared_too_large = True
        if declared_too_large:
            return JSONResponse(status_code=413, content={"code": 413, "message": "request body too large", "data": None})
        if len(await request.body()) > 2 * 1024 * 1024:
            return JSONResponse(status_code=413, content={"code": 413, "message": "request body too large", "data": None})
    return await call_next(request)


@app.middleware("http")
async def log_requests(request: Request, call_next):
    incoming_trace_id = request.headers.get("X-Trace-Id", "")
    trace_id = incoming_trace_id if safe_trace_id.fullmatch(incoming_trace_id) else uuid4().hex
    request.state.trace_id = trace_id
    token = trace_context.set(trace_id)
    start_time = time.perf_counter()

    try:
        logger.info("request started method=%s path=%s", request.method, request.url.path)
        response = await call_next(request)
        duration_ms = round((time.perf_counter() - start_time) * 1000, 2)
        logger.info(
            "request finished method=%s path=%s status=%s duration_ms=%s",
            request.method,
            request.url.path,
            response.status_code,
            duration_ms,
        )
        response.headers["X-Trace-Id"] = trace_id
        return response
    finally:
        trace_context.reset(token)


@app.get("/health")
async def health(request: Request):
    trace_id = getattr(request.state, "trace_id", None)
    return JSONResponse(
        {
            "code": 0,
            "message": "ok",
            "data": {"service": "python-agent"},
            "trace_id": trace_id,
        }
    )


@app.get("/health/live")
async def liveness(request: Request):
    return await health(request)


@app.get("/health/ready")
async def readiness(request: Request):
    return await health(request)
