alter table agent_run
    add column workflow_run_id bigint null after prompt_version_id,
    add column step_run_id bigint null after workflow_run_id,
    add column provider varchar(100) null after time_taken_ms,
    add column model_name varchar(100) null after provider,
    add column mock_state varchar(20) not null default 'UNKNOWN' after model_name,
    add column trace_id varchar(64) null after mock_state,
    add column error_category varchar(80) null after trace_id,
    add column raw_output_ref varchar(512) null after error_category;

create index idx_agent_run_workflow_run_id on agent_run (workflow_run_id);
create index idx_agent_run_step_run_id on agent_run (step_run_id);
create index idx_agent_run_metric_prompt on agent_run (prompt_version_id, mock_state, created_at);

create table model_call_metric (
    id bigint auto_increment primary key,
    agent_run_id bigint not null,
    workflow_run_id bigint null,
    step_run_id bigint null,
    prompt_version_id bigint null,
    provider varchar(100) null,
    model_name varchar(100) null,
    input_tokens int null,
    output_tokens int null,
    estimated_cost decimal(12, 6) null,
    latency_ms bigint null,
    mock_state varchar(20) not null,
    status varchar(20) not null,
    usage_state varchar(20) not null,
    error_category varchar(80) null,
    trace_id varchar(64) null,
    created_at datetime not null,
    constraint uk_model_call_metric_agent_run unique (agent_run_id),
    constraint fk_model_call_metric_agent_run foreign key (agent_run_id) references agent_run (id)
);

create index idx_model_call_metric_prompt_time on model_call_metric (prompt_version_id, mock_state, created_at);
create index idx_model_call_metric_trace_id on model_call_metric (trace_id);
