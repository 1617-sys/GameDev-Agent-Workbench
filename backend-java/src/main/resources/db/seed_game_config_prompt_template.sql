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
) VALUES (
    UUID(),
    'GAME_CONFIG_GENERATE',
    'GameConfig generation template',
    'You are a game prototype configuration agent. Convert the game design context into a strict JSON GameConfig for a Phaser 3 top_down_collect browser demo. Only output valid JSON. Do not output markdown fences, explanations, or comments.',
    'Task title:\n{title}\n\nGame idea:\n{content}\n\nContext:\n{context}\n\nGenerate one JSON object with fields: version, gameType, title, theme, world, player, collectibles, enemies, exit, winCondition, ui. gameType must be top_down_collect. Keep coordinates inside a 960x540 world. Use Chinese text for title, theme, objective, and labels when suitable. Output valid JSON only.',
    1,
    'ACTIVE',
    NOW(),
    NOW(),
    0
);
