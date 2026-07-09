import logging
import time
from uuid import uuid4

from dotenv import load_dotenv
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

load_dotenv()

from app.routers.agent import router as agent_router

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)s | %(name)s | %(message)s",
)

logger = logging.getLogger("python-agent")

app = FastAPI(title="GameDev Agent Mock API", version="0.1.0")
app.include_router(agent_router)


@app.middleware("http")
async def log_requests(request: Request, call_next):
    trace_id = uuid4().hex
    request.state.trace_id = trace_id
    start_time = time.perf_counter()

    logger.info("request started trace_id=%s method=%s path=%s", trace_id, request.method, request.url.path)
    response = await call_next(request)
    duration_ms = round((time.perf_counter() - start_time) * 1000, 2)
    logger.info(
        "request finished trace_id=%s method=%s path=%s status=%s duration_ms=%s",
        trace_id,
        request.method,
        request.url.path,
        response.status_code,
        duration_ms,
    )
    response.headers["X-Trace-Id"] = trace_id
    return response


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