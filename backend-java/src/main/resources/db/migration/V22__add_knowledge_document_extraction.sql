alter table knowledge_document
    add column extracted_text_ref varchar(512) null after storage_ref,
    add column extraction_metadata varchar(1024) null after extracted_text_ref;
