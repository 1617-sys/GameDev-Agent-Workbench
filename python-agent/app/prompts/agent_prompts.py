from app.schemas.agent import AgentMockRequest


GAME_CONCEPT_SYSTEM_PROMPT = """
You are a professional game concept design agent.
You are good at turning a rough game idea into a clear, practical, and production-oriented game concept document.
Your output should be structured, concrete, and useful for the next agents that design core loops and development tasks.
""".strip()


CORE_LOOP_DESIGN_SYSTEM_PROMPT = """
You are a professional game core loop design agent.
You are good at turning a game concept into a playable core loop with player actions, rewards, progression, and replay motivation.
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
The game must use the top_down_collect format: move, collect items, avoid enemies, and reach the exit.
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

Generate one JSON object with this structure:
{{
  "version": "1.0",
  "gameType": "top_down_collect",
  "title": "short game title",
  "theme": "one sentence theme",
  "world": {{
    "width": 960,
    "height": 540,
    "backgroundColor": "#111827"
  }},
  "player": {{
    "x": 96,
    "y": 96,
    "speed": 220,
    "color": "#60a5fa"
  }},
  "collectibles": [
    {{ "id": "item-1", "x": 260, "y": 140, "label": "item name" }},
    {{ "id": "item-2", "x": 520, "y": 300, "label": "item name" }},
    {{ "id": "item-3", "x": 760, "y": 180, "label": "item name" }}
  ],
  "enemies": [
    {{ "id": "enemy-1", "x": 420, "y": 220, "speed": 90, "patrolAxis": "x", "patrolDistance": 180 }},
    {{ "id": "enemy-2", "x": 700, "y": 380, "speed": 80, "patrolAxis": "y", "patrolDistance": 140 }}
  ],
  "exit": {{
    "x": 860,
    "y": 450,
    "lockedUntilCollected": true
  }},
  "winCondition": {{
    "collectAll": true,
    "reachExit": true
  }},
  "ui": {{
    "objective": "Chinese objective text",
    "controlHint": "WASD / arrow keys movement hint"
  }}
}}

Rules:
1. Output valid JSON only.
2. Keep all coordinates inside the world.
3. Keep the gameplay simple enough for a browser MVP.
4. Use Chinese text for title, theme, objective, and labels when suitable.
""".strip()
