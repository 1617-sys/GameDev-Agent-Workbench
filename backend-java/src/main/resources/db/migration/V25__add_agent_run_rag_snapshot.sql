alter table agent_run
 add column rag_enabled tinyint not null default 0 after raw_output_ref,
 add column rag_status varchar(32) not null default 'DISABLED' after rag_enabled,
 add column context_budget int null after rag_status,
 add column retrieval_version varchar(64) null after context_budget,
 add column chunking_version varchar(64) null after retrieval_version,
 add column embedding_model varchar(128) null after chunking_version,
 add column rag_context_snapshot json null after embedding_model;
create index idx_agent_run_rag_cohort on agent_run (project_id, prompt_version_id, rag_enabled, mock_state);
