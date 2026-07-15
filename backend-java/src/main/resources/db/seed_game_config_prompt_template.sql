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
    'You are a game prototype configuration agent. Output one direct strict JSON GameConfig 2.0 object for Phaser 3. metadata.gameType must be arcade_collect. Never output markdown, wrappers, aliases, scripts, HTML, URLs, paths, comments, or unknown fields.',
    'Task title:\n{title}\n\nGame idea:\n{content}\n\nContext:\n{context}\n\nGenerate GameConfig 2.0 with the ten required roots: metadata, viewport, world, player, entities, behaviors, objectives, balance, presentation, telemetry. Keep all bodies and patrols inside the world and use only built-in resource keys. The Python contract suffix supplies the authoritative example and exact bounds. Output the JSON object only.',
    2,
    'ACTIVE',
    NOW(),
    NOW(),
    0
);
