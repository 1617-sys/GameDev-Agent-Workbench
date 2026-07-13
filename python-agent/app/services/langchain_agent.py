import json
import os
import time

from app.prompts.agent_prompts import GAME_CONFIG_SYSTEM_PROMPT, build_game_config_user_prompt
from app.services.rag_context import render_rag_context
from app.schemas.agent import AgentMockRequest, AgentMockResult
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
    user_prompt_template = payload.user_prompt_template or build_game_config_user_prompt(payload)

    prompt = ChatPromptTemplate.from_messages([
        ("system", system_prompt),
        ("human", user_prompt_template),
    ])

    chain = prompt | build_chat_model() | StrOutputParser()

    start = time.perf_counter()
    result_text = await chain.ainvoke({
        "title": payload.title,
        "content": payload.content,
        "context": payload.context or "",
        "project_uuid": payload.project_uuid or "",
        "user_id": payload.user_id or "",
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
            "Generated a playable top-down collect GameConfig",
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
            "user": user_prompt_template,
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
    if cleaned.startswith("```"):
        cleaned = cleaned.removeprefix("```json").removeprefix("```").removesuffix("```").strip()

    try:
        config = json.loads(cleaned)
    except json.JSONDecodeError:
        config = {}

    return normalize_game_config(config, payload)


def normalize_game_config(config: dict, payload: AgentMockRequest) -> dict:
    world = config.get("world") if isinstance(config.get("world"), dict) else {}
    player = config.get("player") if isinstance(config.get("player"), dict) else {}
    exit_config = config.get("exit") if isinstance(config.get("exit"), dict) else {}
    win_condition = config.get("winCondition") if isinstance(config.get("winCondition"), dict) else {}
    ui = config.get("ui") if isinstance(config.get("ui"), dict) else {}

    collectibles = config.get("collectibles")
    if not isinstance(collectibles, list) or not collectibles:
        collectibles = [
            {"id": "item-1", "x": 260, "y": 140, "label": "Energy Core"},
            {"id": "item-2", "x": 520, "y": 300, "label": "Key"},
            {"id": "item-3", "x": 760, "y": 180, "label": "Gem"},
        ]

    enemies = config.get("enemies")
    if not isinstance(enemies, list):
        enemies = [
            {"id": "enemy-1", "x": 420, "y": 220, "speed": 90, "patrolAxis": "x", "patrolDistance": 180},
            {"id": "enemy-2", "x": 700, "y": 380, "speed": 80, "patrolAxis": "y", "patrolDistance": 140},
        ]

    return {
        "version": str(config.get("version") or "1.0"),
        "gameType": config.get("gameType") or "top_down_collect",
        "title": config.get("title") or payload.title,
        "theme": config.get("theme") or payload.content,
        "world": {
            "width": int(world.get("width") or 960),
            "height": int(world.get("height") or 540),
            "backgroundColor": world.get("backgroundColor") or "#111827",
        },
        "player": {
            "x": int(player.get("x") or 96),
            "y": int(player.get("y") or 96),
            "speed": int(player.get("speed") or 220),
            "color": player.get("color") or "#60a5fa",
        },
        "collectibles": collectibles,
        "enemies": enemies,
        "exit": {
            "x": int(exit_config.get("x") or 860),
            "y": int(exit_config.get("y") or 450),
            "lockedUntilCollected": bool(exit_config.get("lockedUntilCollected", True)),
        },
        "winCondition": {
            "collectAll": bool(win_condition.get("collectAll", True)),
            "reachExit": bool(win_condition.get("reachExit", True)),
        },
        "ui": {
            "objective": ui.get("objective") or "Collect all items, avoid enemies, then reach the exit.",
            "controlHint": ui.get("controlHint") or "Use WASD or arrow keys to move.",
        },
    }
