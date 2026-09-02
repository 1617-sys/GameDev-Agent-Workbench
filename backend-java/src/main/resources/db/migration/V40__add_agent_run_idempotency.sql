alter table agent_run
    add column idempotency_key varchar(128) null after run_uuid,
    add column request_fingerprint char(64) null after idempotency_key;

create unique index uk_agent_run_user_idempotency on agent_run (user_id, idempotency_key);
