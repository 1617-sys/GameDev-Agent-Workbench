package com.example.gameworkbench.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.entity.KnowledgeChunk;
import com.example.gameworkbench.entity.KnowledgeDocument;
import com.example.gameworkbench.mapper.KnowledgeChunkMapper;
import com.example.gameworkbench.mapper.KnowledgeDocumentMapper;

@Service
public class ProjectRetrievalService implements RetrievalService {

    private final EmbeddingProvider provider;
    private final InMemoryVectorStore store;
    private final KnowledgeDocumentMapper documents;
    private final KnowledgeChunkMapper chunks;

    public ProjectRetrievalService(EmbeddingProvider provider, InMemoryVectorStore store,
                                   KnowledgeDocumentMapper documents, KnowledgeChunkMapper chunks) {
        this.provider = provider;
        this.store = store;
        this.documents = documents;
        this.chunks = chunks;
    }

    @Override
    public List<RetrievalCandidate> retrieve(RetrievalRequest request) {
        validate(request);
        List<RetrievalCandidate> candidates = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int used = 0;
        for (var hit : store.search(provider.embed(List.of(request.query())).getFirst(), request.projectId())) {
            var metadata = hit.metadata();
            if (!metadataEligible(request, hit, metadata.get("chunkUuid"))) {
                continue;
            }
            KnowledgeDocument document = documents.selectActiveByUuidAndProject(
                    request.projectId(), metadata.get("documentUuid"));
            if (!documentEligible(document, metadata.get("documentVersion"))
                    || chunks.selectCount(new LambdaQueryWrapper<KnowledgeChunk>()
                            .eq(KnowledgeChunk::getProjectId, request.projectId())
                            .eq(KnowledgeChunk::getDocumentId, document.getId())
                            .eq(KnowledgeChunk::getChunkUuid, metadata.get("chunkUuid"))
                            .eq(KnowledgeChunk::getIndexStatus, "INDEXED")) == 0
                    || !seen.add(metadata.get("chunkUuid"))) {
                continue;
            }
            String textReference = metadata.get("textRef");
            if (textReference == null || used + textReference.length() > request.maxChars()) {
                continue;
            }
            used += textReference.length();
            candidates.add(new RetrievalCandidate(
                    metadata.get("chunkUuid"),
                    metadata.get("documentUuid"),
                    metadata.get("documentVersion"),
                    hit.score(),
                    candidates.size() + 1,
                    textReference,
                    metadata.get("embeddingModel")));
            if (candidates.size() == request.topK()) {
                break;
            }
        }
        return candidates;
    }

    private void validate(RetrievalRequest request) {
        if (request.projectId() == null || request.query() == null || request.query().isBlank()
                || request.query().length() > 2000 || request.topK() < 1 || request.topK() > 20
                || request.maxChars() < 1) {
            throw new BusinessException(ErrorCode.INVALID_PARAM);
        }
    }

    private boolean metadataEligible(RetrievalRequest request, InMemoryVectorStore.Hit hit,
                                     String chunkUuid) {
        return String.valueOf(request.projectId()).equals(hit.metadata().get("projectId"))
                && "ACTIVE".equals(hit.metadata().get("status"))
                && hit.score() >= request.minScore()
                && chunkUuid != null;
    }

    private boolean documentEligible(KnowledgeDocument document, String version) {
        return document != null
                && document.getVersion() != null
                && "READY".equals(document.getStatus())
                && String.valueOf(document.getVersion()).equals(version);
    }
}
