alter table agent_run add column rag_experiment_key char(64) null after rag_context_snapshot;
create index idx_agent_run_rag_experiment on agent_run (project_id, rag_experiment_key, prompt_version_id, provider, model_name, rag_enabled);
