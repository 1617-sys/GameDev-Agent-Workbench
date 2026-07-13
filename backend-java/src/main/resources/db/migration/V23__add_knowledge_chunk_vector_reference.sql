alter table knowledge_chunk
    add column vector_ref varchar(128) null after index_status,
    add column embedding_dimension int null after embedding_model;
create unique index uk_knowledge_chunk_vector_ref on knowledge_chunk (vector_ref);
