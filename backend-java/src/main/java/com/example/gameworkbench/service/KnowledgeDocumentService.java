package com.example.gameworkbench.service;

import java.util.List;

import com.example.gameworkbench.common.enums.KnowledgeDocumentStatus;
import com.example.gameworkbench.common.enums.KnowledgeSourceType;
import com.example.gameworkbench.entity.KnowledgeDocument;

public interface KnowledgeDocumentService {
    KnowledgeDocument create(Long userId, Long projectId, String name, KnowledgeSourceType sourceType,
            String contentHash, String storageRef);
    KnowledgeDocument get(Long userId, Long projectId, String documentUuid);
    List<KnowledgeDocument> list(Long userId, Long projectId);
    KnowledgeDocument transition(Long userId, Long projectId, String documentUuid, KnowledgeDocumentStatus target);
    KnowledgeDocument invalidate(Long userId, Long projectId, String documentUuid);
    void delete(Long userId, Long projectId, String documentUuid);
}
