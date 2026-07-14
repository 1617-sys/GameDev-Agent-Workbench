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
    theme = config.get("theme") if isinstance(config.get("theme"), dict) else {}
    palette = theme.get("palette") if isinstance(theme.get("palette"), dict) else {}
    rules = config.get("rules") if isinstance(config.get("rules"), dict) else {}
    win_condition = config.get("winCondition") if isinstance(config.get("winCondition"), dict) else {}
    ui = config.get("ui") if isinstance(config.get("ui"), dict) else {}

    obstacles = config.get("obstacles")
    if not isinstance(obstacles, list):
        obstacles = [
            {"id": "wall-1", "x": 350, "y": 120, "width": 150, "height": 24},
            {"id": "wall-2", "x": 330, "y": 390, "width": 180, "height": 24},
            {"id": "wall-3", "x": 620, "y": 270, "width": 24, "height": 150},
            {"id": "wall-4", "x": 770, "y": 430, "width": 130, "height": 24},
        ]
    obstacles = [
        {
            "id": obstacle.get("id") or f"wall-{index + 1}",
            "x": int(obstacle.get("x") or 240 + index * 120),
            "y": int(obstacle.get("y") or 180 + index * 60),
            "width": int(obstacle.get("width") or 100),
            "height": int(obstacle.get("height") or 24),
        }
        for index, obstacle in enumerate(obstacles[:8])
        if isinstance(obstacle, dict)
    ]

    items = config.get("items")
    if not isinstance(items, list):
        items = config.get("collectibles")
    if not isinstance(items, list) or not items:
        items = [
            {"id": "item-1", "x": 260, "y": 140, "label": "Energy Core"},
            {"id": "item-2", "x": 520, "y": 300, "label": "Key"},
            {"id": "item-3", "x": 760, "y": 180, "label": "Gem"},
        ]
    items = [
        {**item, "size": int(item.get("size") or 18)}
        for item in items
        if isinstance(item, dict)
    ]

    enemies = config.get("enemies")
    if not isinstance(enemies, list):
        enemies = [
            {"id": "enemy-1", "x": 420, "y": 220, "speed": 90, "patrolAxis": "x", "patrolDistance": 180},
            {"id": "enemy-2", "x": 700, "y": 380, "speed": 80, "patrolAxis": "y", "patrolDistance": 140},
        ]
    enemies = [
        {
            **enemy,
            "size": int(enemy.get("size") or 28),
            "range": int(enemy.get("range") or enemy.get("patrolDistance") or 150),
            "axis": enemy.get("axis") if enemy.get("axis") in {"x", "y"} else enemy.get("patrolAxis", "x"),
        }
        for enemy in enemies
        if isinstance(enemy, dict)
    ]

    return {
        "version": str(config.get("version") or "1.0"),
        "gameType": config.get("gameType") or "top_down_collect",
        "title": config.get("title") or payload.title,
        "theme": {
            "palette": {
                "floor": palette.get("floor") or "#14213d",
                "wall": palette.get("wall") or "#24324a",
                "player": palette.get("player") or "#5eead4",
                "item": palette.get("item") or "#facc15",
                "enemy": palette.get("enemy") or "#fb7185",
                "exit": palette.get("exit") or "#22c55e",
            },
        },
        "world": {
            "width": int(world.get("width") or 960),
            "height": int(world.get("height") or 540),
            "backgroundColor": world.get("backgroundColor") or "#111827",
        },
        "player": {
            "x": int(player.get("x") or 96),
            "y": int(player.get("y") or 96),
            "speed": int(player.get("speed") or 220),
            "size": int(player.get("size") or 28),
            "color": player.get("color") or "#60a5fa",
        },
        "obstacles": obstacles,
        "items": items,
        "enemies": enemies,
        "exit": {
            "x": int(exit_config.get("x") or 860),
            "y": int(exit_config.get("y") or 450),
            "width": int(exit_config.get("width") or 54),
            "height": int(exit_config.get("height") or 72),
            "label": exit_config.get("label") or "EXIT",
        },
        "rules": {
            "targetItems": int(rules.get("targetItems") or len(items)),
            "winCondition": rules.get("winCondition")
            or ("collect_all_then_exit" if win_condition.get("collectAll", True) else "reach_exit"),
            "loseCondition": rules.get("loseCondition") or "touch_enemy",
        },
        "ui": {
            "objective": ui.get("objective") or "Collect all items, avoid enemies, then reach the exit.",
            "controls": ui.get("controls") or ui.get("controlHint") or "Use WASD or arrow keys to move. Press R to restart.",
        },
    }
