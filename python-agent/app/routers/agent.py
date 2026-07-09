import logging

from fastapi import APIRouter, Request

from app.schemas.agent import AgentMockRequest
from app.schemas.common import ApiResponse
from app.services.mock_agent import (
    build_api_design_result,
    build_bug_analysis_result,
    build_prompt_generate_result,
    build_requirement_breakdown_result,
)
from app.services.langchain_agent import run_game_config_agent, run_langchain_agent

logger = logging.getLogger("python-agent.agent")

router = APIRouter(prefix="/agent", tags=["agent"])


def _build_response(request: Request, agent_result):
    trace_id = getattr(request.state, "trace_id", None)
    return ApiResponse(code=0, message="success", data=agent_result, trace_id=trace_id)


@router.post("/requirement-breakdown", response_model=ApiResponse)
async def requirement_breakdown(payload: AgentMockRequest, request: Request):
    logger.info("requirement-breakdown received title=%s content_len=%s", payload.title, len(payload.content))
    result = build_requirement_breakdown_result(payload)
    return _build_response(request, result)


@router.post("/api-design", response_model=ApiResponse)
async def api_design(payload: AgentMockRequest, request: Request):
    logger.info("api-design received title=%s content_len=%s", payload.title, len(payload.content))
    result = build_api_design_result(payload)
    return _build_response(request, result)


@router.post("/bug-analysis", response_model=ApiResponse)
async def bug_analysis(payload: AgentMockRequest, request: Request):
    logger.info("bug-analysis received title=%s content_len=%s", payload.title, len(payload.content))
    result = build_bug_analysis_result(payload)
    return _build_response(request, result)


@router.post("/prompt-generate", response_model=ApiResponse)
async def prompt_generate(payload: AgentMockRequest, request: Request):
    logger.info("prompt-generate received title=%s content_len=%s", payload.title, len(payload.content))
    result = build_prompt_generate_result(payload)
    return _build_response(request, result)

@router.post("/game-concept", response_model=ApiResponse)
async def game_concept(payload: AgentMockRequest, request: Request):
    logger.info("game-concept received title=%s content_len=%s", payload.title, len(payload.content))
    result = await run_langchain_agent("GAME_CONCEPT", payload)
    return _build_response(request, result)


@router.post("/core-loop-design", response_model=ApiResponse)
async def core_loop_design(payload: AgentMockRequest, request: Request):
    logger.info("core-loop-design received title=%s content_len=%s", payload.title, len(payload.content))
    result = await run_langchain_agent("CORE_LOOP_DESIGN", payload)
    return _build_response(request, result)


@router.post("/task-breakdown", response_model=ApiResponse)
async def task_breakdown(payload: AgentMockRequest, request: Request):
    logger.info("task-breakdown received title=%s content_len=%s", payload.title, len(payload.content))
    result = await run_langchain_agent("TASK_BREAKDOWN", payload)
    return _build_response(request, result)


@router.post("/game-config-generate", response_model=ApiResponse)
async def game_config_generate(payload: AgentMockRequest, request: Request):
    logger.info("game-config-generate received title=%s content_len=%s", payload.title, len(payload.content))
    result = await run_game_config_agent(payload)
    return _build_response(request, result)
