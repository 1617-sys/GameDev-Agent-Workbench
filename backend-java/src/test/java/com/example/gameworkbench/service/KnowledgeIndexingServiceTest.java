package com.example.gameworkbench.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.example.gameworkbench.entity.KnowledgeChunk;
import com.example.gameworkbench.entity.KnowledgeDocument;
import com.example.gameworkbench.mapper.KnowledgeChunkMapper;

class KnowledgeIndexingServiceTest {
    KnowledgeChunkMapper mapper = mock(KnowledgeChunkMapper.class); InMemoryVectorStore store = new InMemoryVectorStore();
    KnowledgeStorage storage = new KnowledgeStorage() { public String put(String suffix, byte[] content) { return "text-ref"; } public byte[] read(String reference) { return new byte[0]; } };
    KnowledgeIndexingService service = new KnowledgeIndexingService(new KnowledgeChunker(), new FakeEmbeddingProvider(), store, mapper, storage);
    KnowledgeDocument doc() { KnowledgeDocument d = new KnowledgeDocument(); d.setId(1L); d.setProjectId(2L); d.setDocumentUuid("doc"); d.setVersion(1); d.setDeleted(0); return d; }
    @Test void indexesWithIsolatedMetadataAndIsIdempotent() { when(mapper.selectCount(any())).thenReturn(0L); service.index(doc(), "hello"); ArgumentCaptor<KnowledgeChunk> c = ArgumentCaptor.forClass(KnowledgeChunk.class); verify(mapper).insert(c.capture()); assertThat(store.metadata(c.getValue().getVectorRef())).containsEntry("projectId", "2").containsEntry("textRef", "text-ref"); when(mapper.selectCount(any())).thenReturn(1L); service.index(doc(), "hello"); verify(mapper, times(1)).insert(any(KnowledgeChunk.class)); }
    @Test void doesNotIndexDeletedDocument() { KnowledgeDocument d = doc(); d.setDeleted(1); service.index(d, "hello"); verifyNoInteractions(mapper); }
}
