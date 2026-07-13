package com.example.gameworkbench.dto.knowledge;
import lombok.Builder;
@Builder public record KnowledgeUploadResponse(String documentUuid, String status, String contentHash) { }
