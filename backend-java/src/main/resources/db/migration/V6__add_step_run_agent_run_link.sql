alter table workflow_step_run
    add column agent_run_id bigint null comment '执行该步骤的 agent_run.id' after artifact_type;

create index idx_workflow_step_run_agent_run_id
    on workflow_step_run (agent_run_id);
