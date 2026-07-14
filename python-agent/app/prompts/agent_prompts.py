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
  "theme": {{
    "palette": {{
      "floor": "#14213d",
      "wall": "#24324a",
      "player": "#5eead4",
      "item": "#facc15",
      "enemy": "#fb7185",
      "exit": "#22c55e"
    }}
  }},
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
  "obstacles": [
    {{ "id": "wall-1", "x": 350, "y": 120, "width": 150, "height": 24 }},
    {{ "id": "wall-2", "x": 330, "y": 390, "width": 180, "height": 24 }},
    {{ "id": "wall-3", "x": 620, "y": 270, "width": 24, "height": 150 }},
    {{ "id": "wall-4", "x": 770, "y": 430, "width": 130, "height": 24 }}
  ],
  "items": [
    {{ "id": "item-1", "x": 260, "y": 140, "size": 18, "label": "item name" }},
    {{ "id": "item-2", "x": 520, "y": 300, "size": 18, "label": "item name" }},
    {{ "id": "item-3", "x": 760, "y": 180, "size": 18, "label": "item name" }}
  ],
  "enemies": [
    {{ "id": "enemy-1", "x": 420, "y": 220, "size": 28, "speed": 90, "range": 180, "axis": "x" }},
    {{ "id": "enemy-2", "x": 700, "y": 380, "size": 28, "speed": 80, "range": 140, "axis": "y" }}
  ],
  "exit": {{
    "x": 860,
    "y": 450,
    "width": 54,
    "height": 72,
    "label": "EXIT"
  }},
  "rules": {{
    "targetItems": 3,
    "winCondition": "collect_all_then_exit",
    "loseCondition": "touch_enemy"
  }},
  "ui": {{
    "objective": "Chinese objective text",
    "controls": "WASD / arrow keys movement hint; R restarts the game"
  }}
}}

Rules:
1. Output valid JSON only.
2. Keep all coordinates inside the world.
3. Keep the gameplay simple enough for a browser MVP. Use 3 to 6 obstacles to create routes without blocking the player, items, enemies, or exit.
4. Use Chinese text for title, objective, and labels when suitable.
5. Keep the field names and object structure exactly as shown. Enemy axis must be "x" or "y".
""".strip()
