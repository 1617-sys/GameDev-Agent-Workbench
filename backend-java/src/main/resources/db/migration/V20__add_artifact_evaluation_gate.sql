alter table agent_artifact
 add column runtime_eligible tinyint not null default 0 after validation_summary,
 add column last_evaluation_report_id bigint null after runtime_eligible;
create index idx_agent_artifact_runtime_eligible on agent_artifact (runtime_eligible, last_evaluation_report_id);
