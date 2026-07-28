alter table agent_artifact
    add column content_digest varchar(64) null after content,
    add column source_attempt int null after validation_summary,
    add column source_artifact_uuid varchar(36) null after source_attempt,
    add column runtime_capability_version varchar(50) null after source_artifact_uuid;

update agent_artifact
set content_digest = lower(sha2(content, 256))
where content_digest is null;

update agent_artifact artifact
join (
    select id, row_number() over (partition by step_run_id, artifact_type order by id) as source_attempt
    from agent_artifact
    where step_run_id is not null
) ranked on ranked.id = artifact.id
set artifact.source_attempt = ranked.source_attempt;

update agent_artifact
set source_attempt = 1
where source_attempt is null;

alter table agent_artifact
    modify column content_digest varchar(64) not null,
    modify column source_attempt int not null;

create unique index uk_agent_artifact_step_type_attempt
    on agent_artifact (step_run_id, artifact_type, source_attempt);

insert into workflow_definition_version (workflow_key, version, name, status, definition_json, created_by)
values ('GAME_GENERATE', 2, 'Traceable arcade collect generation workflow', 'ACTIVE',
        JSON_OBJECT('workflowKey', 'GAME_GENERATE', 'version', 2, 'steps', JSON_ARRAY(
                JSON_OBJECT('stepKey', 'game_concept', 'stepOrder', 1, 'agentType', 'GAME_CONCEPT', 'artifactType', 'GAME_CONCEPT_RESULT', 'dependsOn', JSON_ARRAY()),
                JSON_OBJECT('stepKey', 'core_loop_design', 'stepOrder', 2, 'agentType', 'CORE_LOOP_DESIGN', 'artifactType', 'CORE_LOOP_DESIGN_RESULT', 'dependsOn', JSON_ARRAY('game_concept')),
                JSON_OBJECT('stepKey', 'task_breakdown', 'stepOrder', 3, 'agentType', 'TASK_BREAKDOWN', 'artifactType', 'TASK_BREAKDOWN_RESULT', 'dependsOn', JSON_ARRAY('game_concept', 'core_loop_design')),
                JSON_OBJECT('stepKey', 'game_config_generate', 'stepOrder', 4, 'agentType', 'GAME_CONFIG_GENERATE', 'artifactType', 'GAME_CONFIG', 'dependsOn', JSON_ARRAY('game_concept', 'core_loop_design', 'task_breakdown'))
        )), null);

insert into workflow_step_definition (definition_version_id, step_key, step_order, agent_type, artifact_type, depends_on_step_key, prompt_template_key)
select id, 'game_concept', 1, 'GAME_CONCEPT', 'GAME_CONCEPT_RESULT', null, 'GAME_CONCEPT'
from workflow_definition_version where workflow_key = 'GAME_GENERATE' and version = 2;
insert into workflow_step_definition (definition_version_id, step_key, step_order, agent_type, artifact_type, depends_on_step_key, prompt_template_key)
select id, 'core_loop_design', 2, 'CORE_LOOP_DESIGN', 'CORE_LOOP_DESIGN_RESULT', 'game_concept', 'CORE_LOOP_DESIGN'
from workflow_definition_version where workflow_key = 'GAME_GENERATE' and version = 2;
insert into workflow_step_definition (definition_version_id, step_key, step_order, agent_type, artifact_type, depends_on_step_key, prompt_template_key)
select id, 'task_breakdown', 3, 'TASK_BREAKDOWN', 'TASK_BREAKDOWN_RESULT', 'core_loop_design', 'TASK_BREAKDOWN'
from workflow_definition_version where workflow_key = 'GAME_GENERATE' and version = 2;
insert into workflow_step_definition (definition_version_id, step_key, step_order, agent_type, artifact_type, depends_on_step_key, prompt_template_key)
select id, 'game_config_generate', 4, 'GAME_CONFIG_GENERATE', 'GAME_CONFIG', 'task_breakdown', 'GAME_CONFIG_GENERATE'
from workflow_definition_version where workflow_key = 'GAME_GENERATE' and version = 2;

update prompt_template
set system_prompt = concat(system_prompt, '\nThe Prototype Brief JSON fields theme, durationSeconds, difficulty, visualTheme and additionalRequirements are authoritative. Stay inside the arcade_collect template.'),
    version = version + 1,
    updated_at = current_timestamp
where agent_type in ('GAME_CONCEPT', 'CORE_LOOP_DESIGN', 'TASK_BREAKDOWN')
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
    latest.output_schema_key, latest.output_schema_version,
    latest.model_parameters_json, 'ACTIVE', null, current_timestamp, current_timestamp, 0
from prompt_template template
left join (
    select template_id, max(version) as max_version
    from prompt_version
    group by template_id
) existing on existing.template_id = template.id
left join prompt_version latest
    on latest.template_id = template.id
   and latest.version = existing.max_version
   and latest.deleted = 0
where template.agent_type in ('GAME_CONCEPT', 'CORE_LOOP_DESIGN', 'TASK_BREAKDOWN')
  and template.status = 'ACTIVE'
  and template.deleted = 0;
