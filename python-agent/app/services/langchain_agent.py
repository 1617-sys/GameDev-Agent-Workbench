import json
import os
import time

from app.prompts.agent_prompts import (
    GAME_CONFIG_SYSTEM_PROMPT,
    build_game_config_user_prompt,
    game_config_contract_suffix,
)
from app.services.rag_context import render_rag_context
from app.schemas.agent import AgentMockRequest, AgentMockResult
from app.schemas.game_config import validate_game_config_v2
from langchain_core.output_parsers import StrOutputParser
from langchain_core.prompts import ChatPromptTemplate
from langchain_openai import ChatOpenAI


DEFAULT_SYSTEM_PROMPT = """
You are a professional game design agent.
You should generate clear, structured, practical game design content.
"""

DEFAULT_USER_PROMPT_TEMPLATE = """
Task title:
{title}

User input:
{content}

Context:
{context}

Please generate a structured game design response in Chinese.
"""


def build_chat_model() -> ChatOpenAI:
    api_key = os.getenv("LLM_API_KEY")
    base_url = os.getenv("LLM_BASE_URL", "https://api.deepseek.com")
    model = os.getenv("LLM_MODEL", "deepseek-chat")

    if not api_key:
        raise RuntimeError("LLM_API_KEY is missing")

    return ChatOpenAI(
        model=model,
        api_key=api_key,
        base_url=base_url,
        temperature=0.7,
    )


async def run_langchain_agent(agent_type: str, payload: AgentMockRequest) -> AgentMockResult:
    system_prompt = payload.system_prompt or DEFAULT_SYSTEM_PROMPT
    user_prompt_template = payload.user_prompt_template or DEFAULT_USER_PROMPT_TEMPLATE

    rag_text, _ = render_rag_context(payload.rag)
    prompt = ChatPromptTemplate.from_messages([
        ("system", system_prompt),
        ("human", user_prompt_template),
    ])

    chain = prompt | build_chat_model() | StrOutputParser()

    start = time.perf_counter()
    result_text = await chain.ainvoke({
        "title": payload.title,
        "content": payload.content,
        "context": (payload.context or "") + rag_text,
    })
    time_taken_ms = int((time.perf_counter() - start) * 1000)

    return AgentMockResult(
        agent_type=agent_type,
        title=payload.title,
        summary=f"{agent_type} generated successfully",
        content=result_text,
        key_points=[],
        suggestions=[],
        model=os.getenv("LLM_MODEL", "deepseek-chat"),
        time_taken_ms=time_taken_ms,
        prompt={
            "system": system_prompt,
            "user": user_prompt_template,
        },
        template={
            "template_uuid": payload.template_uuid,
            "template_version": payload.template_version,
        },
        raw_result={
            "source": "langchain",
            "agent_type": agent_type,
            "project_uuid": payload.project_uuid,
        },
    )


async def run_game_config_agent(payload: AgentMockRequest) -> AgentMockResult:
    system_prompt = payload.system_prompt or GAME_CONFIG_SYSTEM_PROMPT
    if payload.user_prompt_template:
        user_prompt = render_allowed_template(payload.user_prompt_template, payload)
        user_prompt = f"{user_prompt}\n\n{game_config_contract_suffix()}"
    else:
        user_prompt = build_game_config_user_prompt(payload)

    rag_text, _ = render_rag_context(payload.rag)
    prompt = ChatPromptTemplate.from_messages([
        ("system", "{system_prompt}"),
        ("human", "{user_prompt}"),
    ])

    chain = prompt | build_chat_model() | StrOutputParser()

    start = time.perf_counter()
    result_text = await chain.ainvoke({
        "system_prompt": system_prompt,
        "user_prompt": user_prompt + rag_text,
    })
    time_taken_ms = int((time.perf_counter() - start) * 1000)
    game_config = parse_game_config(result_text, payload)

    return AgentMockResult(
        agent_type="GAME_CONFIG_GENERATE",
        title=payload.title,
        summary="GameConfig generated successfully",
        content=json.dumps(game_config, ensure_ascii=False, indent=2),
        game_config=game_config,
        key_points=[
            "Generated a validated arcade_collect GameConfig 2.0",
            "The result can be rendered by the Vue3 Phaser runtime",
        ],
        suggestions=[
            "Save this GameConfig as an artifact",
            "Open /demo/play with this config to test the playable prototype",
        ],
        model=os.getenv("LLM_MODEL", "deepseek-chat"),
        time_taken_ms=time_taken_ms,
        prompt={
            "system": system_prompt,
            "user": user_prompt,
        },
        template={
            "template_uuid": payload.template_uuid,
            "template_version": payload.template_version,
        },
        raw_result={
            "source": "langchain",
            "agent_type": "GAME_CONFIG_GENERATE",
            "project_uuid": payload.project_uuid,
            "raw_text": result_text,
        },
    )


def parse_game_config(result_text: str, payload: AgentMockRequest) -> dict:
    cleaned = result_text.strip()
    try:
        config = json.loads(cleaned)
    except json.JSONDecodeError as exception:
        raise ValueError("GameConfig output must be one direct valid JSON object") from exception
    if not isinstance(config, dict):
        raise ValueError("GameConfig output must be one direct JSON object")
    return validate_game_config_v2(config)


def render_allowed_template(template: str, payload: AgentMockRequest) -> str:
    values = {
        "{title}": payload.title,
        "{content}": payload.content,
        "{context}": payload.context or "No extra context.",
        "{project_uuid}": payload.project_uuid or "",
        "{user_id}": str(payload.user_id or ""),
    }
    rendered = template
    for marker, value in values.items():
        rendered = rendered.replace(marker, value)
    return rendered
