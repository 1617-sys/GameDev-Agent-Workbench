alter table workflow_run
    add column trace_id varchar(64) null comment 'Stable async workflow trace identifier' after request_fingerprint;

create index idx_workflow_run_trace_id on workflow_run (trace_id);
