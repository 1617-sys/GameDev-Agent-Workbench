create table knowledge_document
(
    id           bigint auto_increment primary key,
    document_uuid varchar(36) not null,
    project_id    bigint not null,
    name          varchar(255) not null,
    source_type   varchar(32) not null,
    content_hash  char(64) not null,
    version       int not null,
    status        varchar(20) not null,
    storage_ref   varchar(512) null,
    parsed_at     datetime null,
    indexed_at    datetime null,
    deleted_at    datetime null,
    failure_reason varchar(255) null,
    created_at    datetime not null default current_timestamp,
    updated_at    datetime not null default current_timestamp on update current_timestamp,
    deleted       tinyint not null default 0,
    constraint uk_knowledge_document_uuid unique (document_uuid),
    constraint uk_knowledge_document_project_hash unique (project_id, content_hash),
    constraint uk_knowledge_document_project_version unique (project_id, version),
    constraint uk_knowledge_document_id_project unique (id, project_id),
    constraint fk_knowledge_document_project foreign key (project_id) references game_project (id)
) comment 'project-isolated, versioned knowledge document metadata';

create index idx_knowledge_document_project_status on knowledge_document (project_id, status, deleted);
create index idx_knowledge_document_project_deleted_at on knowledge_document (project_id, deleted_at);

create table knowledge_chunk
(
    id               bigint auto_increment primary key,
    chunk_uuid       varchar(36) not null,
    document_id      bigint not null,
    project_id       bigint not null,
    ordinal          int not null,
    text_ref         varchar(512) null,
    text_hash        char(64) not null,
    token_count      int not null,
    chunking_version varchar(64) not null,
    embedding_model  varchar(128) null,
    index_status     varchar(20) not null,
    created_at       datetime not null default current_timestamp,
    updated_at       datetime not null default current_timestamp on update current_timestamp,
    deleted          tinyint not null default 0,
    constraint uk_knowledge_chunk_uuid unique (chunk_uuid),
    constraint uk_knowledge_chunk_document_ordinal unique (document_id, ordinal),
    constraint fk_knowledge_chunk_document_project foreign key (document_id, project_id)
        references knowledge_document (id, project_id)
) comment 'knowledge document chunks; contents are stored separately';

create index idx_knowledge_chunk_project_index on knowledge_chunk (project_id, index_status, deleted);
create index idx_knowledge_chunk_document on knowledge_chunk (document_id, deleted);
