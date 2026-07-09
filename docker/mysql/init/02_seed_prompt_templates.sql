USE gamedev_agent_workbench;

INSERT INTO prompt_template (
    template_uuid,
    agent_type,
    name,
    system_prompt,
    user_prompt_template,
    version,
    status,
    created_at,
    updated_at,
    deleted
)
SELECT
    UUID(),
    'GAME_CONCEPT',
    'Default game concept template',
    'You are a professional game concept design agent. Turn a rough game idea into a clear, practical and production-oriented game concept document.',
    'Task title:\n{title}\n\nGame idea:\n{content}\n\nContext:\n{context}\n\nPlease generate a structured game concept document in Chinese. Include: one-sentence concept, target players, core fantasy, gameplay direction, art style, MVP scope and risks.',
    1,
    'ACTIVE',
    NOW(),
    NOW(),
    0
WHERE NOT EXISTS (
    SELECT 1 FROM prompt_template WHERE agent_type = 'GAME_CONCEPT' AND status = 'ACTIVE' AND deleted = 0
);

INSERT INTO prompt_template (
    template_uuid,
    agent_type,
    name,
    system_prompt,
    user_prompt_template,
    version,
    status,
    created_at,
    updated_at,
    deleted
)
SELECT
    UUID(),
    'CORE_LOOP_DESIGN',
    'Default core loop template',
    'You are a professional game core loop design agent. Convert a game concept into a playable loop with player actions, rewards, feedback and progression.',
    'Task title:\n{title}\n\nGame idea:\n{content}\n\nUpstream context:\n{context}\n\nPlease design the core loop in Chinese. Include: player loop, player actions, rewards, feedback, progression, failure pressure and MVP implementation suggestions.',
    1,
    'ACTIVE',
    NOW(),
    NOW(),
    0
WHERE NOT EXISTS (
    SELECT 1 FROM prompt_template WHERE agent_type = 'CORE_LOOP_DESIGN' AND status = 'ACTIVE' AND deleted = 0
);

INSERT INTO prompt_template (
    template_uuid,
    agent_type,
    name,
    system_prompt,
    user_prompt_template,
    version,
    status,
    created_at,
    updated_at,
    deleted
)
SELECT
    UUID(),
    'TASK_BREAKDOWN',
    'Default task breakdown template',
    'You are a professional game development task breakdown agent. Convert design documents into concrete MVP implementation tasks.',
    'Task title:\n{title}\n\nGame idea:\n{content}\n\nDesign context:\n{context}\n\nPlease break this into MVP development tasks in Chinese. Include backend, Python Agent, frontend, database, testing, implementation order and risks.',
    1,
    'ACTIVE',
    NOW(),
    NOW(),
    0
WHERE NOT EXISTS (
    SELECT 1 FROM prompt_template WHERE agent_type = 'TASK_BREAKDOWN' AND status = 'ACTIVE' AND deleted = 0
);

INSERT INTO prompt_template (
    template_uuid,
    agent_type,
    name,
    system_prompt,
    user_prompt_template,
    version,
    status,
    created_at,
    updated_at,
    deleted
)
SELECT
    UUID(),
    'GAME_CONFIG_GENERATE',
    'Default GameConfig template',
    'You are a game prototype configuration agent. Convert game design context into a strict JSON GameConfig for a Phaser 3 top_down_collect browser demo. Only output valid JSON.',
    'Task title:\n{title}\n\nGame idea:\n{content}\n\nContext:\n{context}\n\nGenerate one JSON object with fields: version, gameType, title, theme, world, player, collectibles, enemies, exit, winCondition, ui. gameType must be top_down_collect. Keep coordinates inside a 960x540 world. Use Chinese text for title, theme, objective and labels when suitable. Output valid JSON only.',
    1,
    'ACTIVE',
    NOW(),
    NOW(),
    0
WHERE NOT EXISTS (
    SELECT 1 FROM prompt_template WHERE agent_type = 'GAME_CONFIG_GENERATE' AND status = 'ACTIVE' AND deleted = 0
);
