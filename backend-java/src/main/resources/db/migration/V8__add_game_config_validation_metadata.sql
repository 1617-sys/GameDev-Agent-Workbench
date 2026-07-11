alter table agent_artifact
    add column schema_key varchar(80) null comment '结构化产物 schema 标识' after content,
    add column schema_version varchar(40) null comment '结构化产物 schema 版本' after schema_key,
    add column validation_summary varchar(500) null comment '结构化产物校验摘要' after schema_version;

alter table workflow_step_run
    add column schema_key varchar(80) null comment '步骤产物 schema 标识' after output_snapshot,
    add column schema_version varchar(40) null comment '步骤产物 schema 版本' after schema_key,
    add column validation_summary varchar(500) null comment '步骤产物校验摘要' after schema_version;
