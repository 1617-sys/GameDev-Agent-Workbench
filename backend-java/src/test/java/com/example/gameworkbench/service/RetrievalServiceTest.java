package com.example.gameworkbench.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.gameworkbench.entity.KnowledgeDocument;
import com.example.gameworkbench.mapper.KnowledgeChunkMapper;
import com.example.gameworkbench.mapper.KnowledgeDocumentMapper;

class RetrievalServiceTest {

    @Test
    void enforcesVectorAndDatabaseProjectStatusFilters() {
        Fixture fixture = new Fixture();
        fixture.store.upsert("a", fixture.vector(), metadata("1", "c", "d", "text-a"));
        fixture.store.upsert("b", fixture.vector(), metadata("2", "x", "z", "text-b"));
        when(fixture.documents.selectActiveByUuidAndProject(1L, "d"))
                .thenReturn(document("READY"));
        when(fixture.chunks.selectCount(any())).thenReturn(1L);

        var result = fixture.service().retrieve(new RetrievalRequest(1L, "q", 5, 0, 100));

        assertThat(result).singleElement().extracting(RetrievalCandidate::chunkUuid).isEqualTo("c");
    }

    @Test
    void excludesStaleVectorWhenDocumentIsInvalid() {
        Fixture fixture = new Fixture();
        fixture.store.upsert("a", fixture.vector(), metadata("1", "c", "d", "text-a"));
        when(fixture.documents.selectActiveByUuidAndProject(1L, "d"))
                .thenReturn(document("INVALID"));

        assertThat(fixture.service().retrieve(new RetrievalRequest(1L, "q", 5, 0, 100))).isEmpty();
    }

    private static Map<String, String> metadata(String projectId, String chunkUuid,
                                                String documentUuid, String textReference) {
        return Map.of(
                "projectId", projectId,
                "status", "ACTIVE",
                "chunkUuid", chunkUuid,
                "documentUuid", documentUuid,
                "documentVersion", "1",
                "embeddingModel", "fake-hash-v1",
                "textRef", textReference
        );
    }

    private static KnowledgeDocument document(String status) {
        return KnowledgeDocument.builder().id(10L).projectId(1L).version(1).status(status).build();
    }

    private static class Fixture {
        final InMemoryVectorStore store = new InMemoryVectorStore();
        final FakeEmbeddingProvider provider = new FakeEmbeddingProvider();
        final KnowledgeDocumentMapper documents = mock(KnowledgeDocumentMapper.class);
        final KnowledgeChunkMapper chunks = mock(KnowledgeChunkMapper.class);

        ProjectRetrievalService service() {
            return new ProjectRetrievalService(provider, store, documents, chunks);
        }

        float[] vector() {
            return provider.embed(List.of("q")).getFirst();
        }
    }
}
