update prompt_template
set system_prompt = 'You are a game prototype configuration agent. Output one direct strict JSON GameConfig 2.0 object for Phaser 3. metadata.gameType must be arcade_collect. Never output markdown, wrappers, aliases, scripts, HTML, URLs, paths, comments, or unknown fields.',
    user_prompt_template = 'Task title:\n{title}\n\nGame idea:\n{content}\n\nContext:\n{context}\n\nGenerate GameConfig 2.0 with the ten required roots: metadata, viewport, world, player, entities, behaviors, objectives, balance, presentation, telemetry. Keep all bodies and patrols inside the world and use only built-in resource keys. The Python contract suffix supplies the authoritative example and exact bounds. Output the JSON object only.',
    version = version + 1,
    updated_at = current_timestamp
where agent_type = 'GAME_CONFIG_GENERATE'
  and status = 'ACTIVE'
  and deleted = 0;

insert into prompt_version (
    version_uuid, template_id, template_uuid, agent_type, version, name,
    system_prompt, user_prompt_template, output_schema_key, output_schema_version,
    model_parameters_json, status, created_by, created_at, updated_at, deleted
)
select
    UUID(), template.id, template.template_uuid, template.agent_type,
    coalesce(existing.max_version, 0) + 1,
    template.name, template.system_prompt, template.user_prompt_template,
    'game-config', '2.0', null, 'ACTIVE', null, current_timestamp, current_timestamp, 0
from prompt_template template
left join (
    select template_id, max(version) as max_version
    from prompt_version
    group by template_id
) existing on existing.template_id = template.id
where template.agent_type = 'GAME_CONFIG_GENERATE'
  and template.status = 'ACTIVE'
  and template.deleted = 0;
