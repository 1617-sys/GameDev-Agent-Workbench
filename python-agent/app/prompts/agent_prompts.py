import json
from pathlib import Path

from app.schemas.agent import AgentMockRequest


GAME_CONCEPT_SYSTEM_PROMPT = """
You are a professional game concept design agent.
You only design the frozen arcade_collect prototype: move, collect targets, avoid patrol enemies, then reach the exit.
Preserve the Prototype Brief theme, duration, difficulty, visualTheme, and additionalRequirements without inventing another template.
Your output should be structured, concrete, and useful for the next agents that design core loops and development tasks.
""".strip()


CORE_LOOP_DESIGN_SYSTEM_PROMPT = """
You are a professional game core loop design agent.
You only design the frozen arcade_collect loop and must preserve every Prototype Brief constraint.
Do not add combat, multiple levels, narrative systems, progression trees, multiplayer, or custom runtime code.
Your output should be concrete enough for a product manager or developer to continue breaking down tasks.
""".strip()


TASK_BREAKDOWN_SYSTEM_PROMPT = """
You are a professional game development task breakdown agent.
You are good at turning game design documents into practical MVP development tasks.
Your output should be executable, ordered, and useful for a small development team.
""".strip()


GAME_CONFIG_SYSTEM_PROMPT = """
You are a game prototype configuration agent.
Your job is to convert a lightweight game idea into a strict JSON GameConfig for a Phaser 3 browser demo.
Only output valid JSON. Do not output markdown fences, explanations, or comments.
The only output contract is GameConfig 2.0 with metadata.gameType=arcade_collect.
Never output scripts, HTML, URLs, file paths, data URLs, aliases, wrappers, or unknown fields.
Every required structure and gameplay value must be authored explicitly; do not rely on defaults.
""".strip()


_GAME_CONFIG_EXAMPLE_PATH = Path(__file__).resolve().parents[1] / "contracts" / "game-config-2.0.valid.json"


def canonical_game_config_example() -> dict:
    return json.loads(_GAME_CONFIG_EXAMPLE_PATH.read_text(encoding="utf-8"))


def game_config_contract_suffix() -> str:
    example = json.dumps(canonical_game_config_example(), ensure_ascii=False, indent=2)
    return f"""

The exact authoritative GameConfig 2.0 example follows. Keep its object shape and field names; change only values within the allowed bounds:
{example}

Contract rules:
1. All ten root objects are required and unknown fields are forbidden at every level.
2. JSON numbers must be numbers, not strings. Keep viewport/world 640-1280 by 360-720, equal to each other and within 1% of 16:9.
3. Keep every body and patrol inside world bounds. Use 1-20 collectibles, 0-12 enemies, 0-16 obstacles, and exactly one patrol per enemy.
4. Use only resource keys shown by the example categories and the RFC allow-list. Never output a URL, path, script, HTML, markdown, wrapper, or legacy alias.
5. telemetry.events must contain exactly the seven values in the example.
6. Output the JSON object only.
""".strip()


def build_game_concept_user_prompt(payload: AgentMockRequest) -> str:
    context = payload.context or "No extra context."
    return f"""
Task title:
{payload.title}

Game idea / user input:
{payload.content}

Extra context:
{context}

Please generate a game concept document with these sections:
1. One-sentence concept
2. Target players
3. Core fantasy and selling point
4. Main gameplay direction
5. Art style and tone
6. MVP scope suggestion
7. Risks and follow-up design questions

Please write in Chinese, but keep the structure clear and suitable for saving as a project artifact.
""".strip()


def build_core_loop_design_user_prompt(payload: AgentMockRequest) -> str:
    context = payload.context or "No extra context."
    return f"""
Task title:
{payload.title}

Current game concept / user input:
{payload.content}

Previous context or upstream agent output:
{context}

Please design the core loop with these sections:
1. Core loop in one sentence
2. Step-by-step player loop
3. Main player actions
4. Reward and feedback design
5. Progression and growth design
6. Failure, challenge, and replay motivation
7. MVP implementation suggestion

Please write in Chinese and make the result suitable for the next task-breakdown agent.
""".strip()


def build_task_breakdown_user_prompt(payload: AgentMockRequest) -> str:
    context = payload.context or "No extra context."
    return f"""
Task title:
{payload.title}

Design goal / user input:
{payload.content}

Game concept and core loop context:
{context}

Please break this into MVP development tasks with these sections:
1. Development milestone overview
2. Backend tasks
3. Python Agent tasks
4. Frontend tasks
5. Database and data model tasks
6. Testing and debugging tasks
7. Recommended implementation order
8. Risks and optional improvements

Please write in Chinese. Each task should be specific enough that a junior developer can start coding.
""".strip()


def build_game_config_user_prompt(payload: AgentMockRequest) -> str:
    context = payload.context or "No extra context."
    return f"""
Task title:
{payload.title}

Game idea:
{payload.content}

Context:
{context}

{game_config_contract_suffix()}
""".strip()
