create table evaluation_report (
 id bigint auto_increment primary key,
 artifact_id bigint not null,
 evaluator_type varchar(20) not null,
 status varchar(20) not null,
 schema_key varchar(80) null,
 schema_version varchar(20) null,
 input_hash varchar(64) not null,
 violations_json json not null,
 evaluation_attempt int not null default 1,
 evaluated_at datetime not null,
 unique key uk_evaluation_report_attempt (artifact_id, evaluator_type, evaluation_attempt),
 constraint fk_evaluation_report_artifact foreign key (artifact_id) references agent_artifact(id)
);
create index idx_evaluation_report_artifact on evaluation_report (artifact_id, evaluator_type, evaluated_at);
