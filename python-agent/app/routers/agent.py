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
from app.services.balance_evaluation import run_balance_evaluation
from app.services.rag_context import render_rag_context

logger = logging.getLogger("python-agent.agent")

router = APIRouter(prefix="/agent", tags=["agent"])


def _build_response(request: Request, agent_result, payload: AgentMockRequest | None = None):
    trace_id = getattr(request.state, "trace_id", None)
    output = agent_result.model_dump(exclude={"raw_result", "prompt", "template"})
    is_mock = agent_result.model == "mock" or agent_result.raw_result.get("mode") in {"mock", "mock_fallback"}
    _, used_references = render_rag_context(payload.rag if payload is not None else None)
    return ApiResponse(
        code=0,
        message="success",
        data={
            "status": "SUCCESS",
            "output": output,
            "raw_output_ref": None,
            "model": agent_result.model,
            "provider": "mock" if is_mock else "openai-compatible",
            "usage": None,
            "latency_ms": agent_result.time_taken_ms,
            "mock": is_mock,
            "rag_status": "DISABLED" if payload is None or payload.rag is None or not payload.rag.rag_enabled else ("AVAILABLE" if used_references else "EMPTY"),
            "used_references": used_references,
        },
        trace_id=trace_id,
    )


@router.post("/requirement-breakdown", response_model=ApiResponse)
async def requirement_breakdown(payload: AgentMockRequest, request: Request):
    logger.info("agent request received agent_type=requirement-breakdown content_len=%s", len(payload.content))
    result = build_requirement_breakdown_result(payload)
    return _build_response(request, result, payload)


@router.post("/api-design", response_model=ApiResponse)
async def api_design(payload: AgentMockRequest, request: Request):
    logger.info("agent request received agent_type=api-design content_len=%s", len(payload.content))
    result = build_api_design_result(payload)
    return _build_response(request, result, payload)


@router.post("/bug-analysis", response_model=ApiResponse)
async def bug_analysis(payload: AgentMockRequest, request: Request):
    logger.info("agent request received agent_type=bug-analysis content_len=%s", len(payload.content))
    result = build_bug_analysis_result(payload)
    return _build_response(request, result, payload)


@router.post("/prompt-generate", response_model=ApiResponse)
async def prompt_generate(payload: AgentMockRequest, request: Request):
    logger.info("agent request received agent_type=prompt-generate content_len=%s", len(payload.content))
    result = build_prompt_generate_result(payload)
    return _build_response(request, result, payload)

@router.post("/game-concept", response_model=ApiResponse)
async def game_concept(payload: AgentMockRequest, request: Request):
    logger.info("agent request received agent_type=game-concept content_len=%s", len(payload.content))
    result = await run_langchain_agent("GAME_CONCEPT", payload)
    return _build_response(request, result, payload)


@router.post("/core-loop-design", response_model=ApiResponse)
async def core_loop_design(payload: AgentMockRequest, request: Request):
    logger.info("agent request received agent_type=core-loop-design content_len=%s", len(payload.content))
    result = await run_langchain_agent("CORE_LOOP_DESIGN", payload)
    return _build_response(request, result, payload)


@router.post("/task-breakdown", response_model=ApiResponse)
async def task_breakdown(payload: AgentMockRequest, request: Request):
    logger.info("agent request received agent_type=task-breakdown content_len=%s", len(payload.content))
    result = await run_langchain_agent("TASK_BREAKDOWN", payload)
    return _build_response(request, result, payload)


@router.post("/game-config-generate", response_model=ApiResponse)
async def game_config_generate(payload: AgentMockRequest, request: Request):
    logger.info("agent request received agent_type=game-config-generate content_len=%s", len(payload.content))
    result = await run_game_config_agent(payload)
    return _build_response(request, result, payload)

@router.post("/balance-evaluation", response_model=ApiResponse)
async def balance_evaluation(payload: AgentMockRequest, request: Request):
    logger.info("agent request received agent_type=balance-evaluation content_len=%s", len(payload.content))
    result = await run_balance_evaluation(payload)
    return _build_response(request, result, payload)
