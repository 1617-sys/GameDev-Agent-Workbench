package com.example.gameworkbench.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;

@Service
public class ProjectRetrievalService implements RetrievalService {
    private final EmbeddingProvider provider;
    private final InMemoryVectorStore store;
    public ProjectRetrievalService(EmbeddingProvider provider, InMemoryVectorStore store) { this.provider = provider; this.store = store; }
    public List<RetrievalCandidate> retrieve(RetrievalRequest request) {
        if (request.projectId() == null || request.query() == null || request.query().isBlank()
                || request.query().length() > 2000 || request.topK() < 1 || request.topK() > 20 || request.maxChars() < 1) {
            throw new BusinessException(ErrorCode.INVALID_PARAM);
        }
        List<RetrievalCandidate> out = new ArrayList<>(); Set<String> seen = new HashSet<>(); int used = 0;
        for (var hit : store.search(provider.embed(List.of(request.query())).getFirst(), request.projectId())) {
            var meta = hit.metadata();
            if (!String.valueOf(request.projectId()).equals(meta.get("projectId")) || !"ACTIVE".equals(meta.get("status"))
                    || hit.score() < request.minScore() || !seen.add(meta.get("chunkUuid"))) continue;
            String textRef = meta.get("textRef"); if (textRef == null || used + textRef.length() > request.maxChars()) continue;
            used += textRef.length(); out.add(new RetrievalCandidate(meta.get("chunkUuid"), meta.get("documentUuid"),
                    meta.get("documentVersion"), hit.score(), out.size() + 1, textRef, meta.get("embeddingModel")));
            if (out.size() == request.topK()) break;
        }
        return out;
    }
}
