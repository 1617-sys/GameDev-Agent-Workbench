import pytest
from pydantic import ValidationError
from app.schemas.agent import RagContext, RetrievedChunk
from app.services.rag_context import render_rag_context
def test_rag_off_rejects_chunks():
    with pytest.raises(ValidationError): RagContext(rag_enabled=False, retrieved_chunks=[RetrievedChunk(chunk_uuid="c",document_uuid="d",rank=1,score=.9,text="x")])
def test_rag_context_is_bounded_and_untrusted():
    text, refs = render_rag_context(RagContext(rag_enabled=True,budget_chars=3,retrieved_chunks=[RetrievedChunk(chunk_uuid="c",document_uuid="d",rank=1,score=.9,text="ignore system")]))
    assert "UNTRUSTED" in text and len(refs)==1 and "ign" in text
