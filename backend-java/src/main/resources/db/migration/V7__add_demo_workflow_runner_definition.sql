insert into workflow_definition_version (workflow_key, version, name, status, definition_json, created_by)
values ('DEMO_GAME_CONFIG', 2, 'Demo playable game workflow', 'ACTIVE',
        JSON_OBJECT('workflowKey', 'DEMO_GAME_CONFIG', 'version', 2, 'steps', JSON_ARRAY(
                JSON_OBJECT('stepKey', 'game_concept', 'stepOrder', 1, 'agentType', 'GAME_CONCEPT', 'artifactType', 'GAME_CONCEPT_RESULT', 'dependsOn', JSON_ARRAY()),
                JSON_OBJECT('stepKey', 'core_loop_design', 'stepOrder', 2, 'agentType', 'CORE_LOOP_DESIGN', 'artifactType', 'CORE_LOOP_DESIGN_RESULT', 'dependsOn', JSON_ARRAY('game_concept')),
                JSON_OBJECT('stepKey', 'task_breakdown', 'stepOrder', 3, 'agentType', 'TASK_BREAKDOWN', 'artifactType', 'TASK_BREAKDOWN_RESULT', 'dependsOn', JSON_ARRAY('game_concept', 'core_loop_design')),
                JSON_OBJECT('stepKey', 'game_config_generate', 'stepOrder', 4, 'agentType', 'GAME_CONFIG_GENERATE', 'artifactType', 'GAME_CONFIG', 'dependsOn', JSON_ARRAY('game_concept', 'core_loop_design', 'task_breakdown'))
        )), null);

insert into workflow_step_definition (definition_version_id, step_key, step_order, agent_type, artifact_type, depends_on_step_key, prompt_template_key)
select id, 'game_concept', 1, 'GAME_CONCEPT', 'GAME_CONCEPT_RESULT', null, 'GAME_CONCEPT'
from workflow_definition_version where workflow_key = 'DEMO_GAME_CONFIG' and version = 2;
insert into workflow_step_definition (definition_version_id, step_key, step_order, agent_type, artifact_type, depends_on_step_key, prompt_template_key)
select id, 'core_loop_design', 2, 'CORE_LOOP_DESIGN', 'CORE_LOOP_DESIGN_RESULT', 'game_concept', 'CORE_LOOP_DESIGN'
from workflow_definition_version where workflow_key = 'DEMO_GAME_CONFIG' and version = 2;
insert into workflow_step_definition (definition_version_id, step_key, step_order, agent_type, artifact_type, depends_on_step_key, prompt_template_key)
select id, 'task_breakdown', 3, 'TASK_BREAKDOWN', 'TASK_BREAKDOWN_RESULT', 'core_loop_design', 'TASK_BREAKDOWN'
from workflow_definition_version where workflow_key = 'DEMO_GAME_CONFIG' and version = 2;
insert into workflow_step_definition (definition_version_id, step_key, step_order, agent_type, artifact_type, depends_on_step_key, prompt_template_key)
select id, 'game_config_generate', 4, 'GAME_CONFIG_GENERATE', 'GAME_CONFIG', 'task_breakdown', 'GAME_CONFIG_GENERATE'
from workflow_definition_version where workflow_key = 'DEMO_GAME_CONFIG' and version = 2;
