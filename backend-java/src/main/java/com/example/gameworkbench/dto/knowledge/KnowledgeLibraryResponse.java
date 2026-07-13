package com.example.gameworkbench.dto.knowledge;

import java.time.LocalDateTime;
import java.util.List;

public record KnowledgeLibraryResponse(
        List<KnowledgeDocumentSummary> documents,
        Capabilities capabilities
) {
    public record Capabilities(boolean upload, boolean invalidate, boolean delete) {
    }

    public record KnowledgeDocumentSummary(
            String documentUuid,
            String name,
            String sourceType,
            String contentHashSummary,
            Integer version,
            String status,
            String failureSummary,
            LocalDateTime parsedAt,
            LocalDateTime indexedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }
}
