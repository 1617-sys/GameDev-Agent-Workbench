alter table workflow_run
    add column command_key varchar(80) null comment 'Last idempotent lifecycle command key' after event_sequence;
