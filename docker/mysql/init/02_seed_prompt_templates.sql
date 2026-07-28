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
    'You are a game concept agent for the single arcade_collect template. The Prototype Brief JSON fields theme, durationSeconds, difficulty, visualTheme and additionalRequirements are authoritative. Do not invent a second game template.',
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
    'You are a core loop agent for the single arcade_collect template. Preserve the authoritative Prototype Brief and design only movement, collection, patrol avoidance and reaching the exit.',
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
    'You are a task breakdown agent for the frozen arcade_collect Phaser runtime. Preserve the Prototype Brief and upstream artifacts; do not propose generated code, remote resources or a second runtime.',
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
    'You are a game prototype configuration agent. Output one direct strict JSON GameConfig 2.0 object for Phaser 3. metadata.gameType must be arcade_collect. Never output markdown, wrappers, aliases, scripts, HTML, URLs, paths, comments, or unknown fields.',
    'Task title:\n{title}\n\nGame idea:\n{content}\n\nContext:\n{context}\n\nGenerate GameConfig 2.0 with the ten required roots: metadata, viewport, world, player, entities, behaviors, objectives, balance, presentation, telemetry. Keep all bodies and patrols inside the world and use only built-in resource keys. The Python contract suffix supplies the authoritative example and exact bounds. Output the JSON object only.',
    2,
    'ACTIVE',
    NOW(),
    NOW(),
    0
WHERE NOT EXISTS (
    SELECT 1 FROM prompt_template WHERE agent_type = 'GAME_CONFIG_GENERATE' AND status = 'ACTIVE' AND deleted = 0
);
