package com.example.gameworkbench.service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.gameworkbench.common.enums.*;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.dto.knowledge.KnowledgeUploadResponse;
import com.example.gameworkbench.entity.*;
import com.example.gameworkbench.mapper.*;
import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor
public class KnowledgeDocumentIngestionService {
    private final GameProjectMapper projects; private final KnowledgeDocumentService documents;
    private final KnowledgeDocumentMapper documentMapper; private final KnowledgeUploadSecurityService security;
    private final KnowledgeStorage storage; private final KnowledgeIndexingService indexing; private final TaskExecutor taskExecutor;
    public KnowledgeUploadResponse upload(Long userId, String projectUuid, MultipartFile file) {
        try {
            GameProject project = projects.selectOne(new LambdaQueryWrapper<GameProject>().eq(GameProject::getProjectUuid, projectUuid).eq(GameProject::getUserId, userId));
            if (project == null) throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
            byte[] body = file.getBytes(); var valid = security.validate(file.getOriginalFilename(), file.getContentType(), body);
            String ref = storage.put("." + valid.extension(), body);
            KnowledgeDocument document = documents.create(userId, project.getId(), file.getOriginalFilename(), KnowledgeSourceType.UPLOAD, valid.contentHash(), ref);
            taskExecutor.execute(() -> parseAndIndex(userId, project.getId(), document.getDocumentUuid(), valid.extension(), ref));
            return KnowledgeUploadResponse.builder().documentUuid(document.getDocumentUuid()).status(document.getStatus()).contentHash(valid.contentHash()).build();
        } catch (BusinessException exception) { throw exception; } catch (Exception exception) { throw new BusinessException(ErrorCode.INVALID_PARAM); }
    }
    public void parseAndIndex(Long userId, Long projectId, String uuid, String extension, String ref) {
        try {
            documents.transition(userId, projectId, uuid, KnowledgeDocumentStatus.PARSING);
            byte[] bytes = storage.read(ref); String text = new String(bytes, StandardCharsets.UTF_8).trim();
            if (text.isBlank()) throw new IllegalArgumentException("empty document");
            KnowledgeDocument document = documents.get(userId, projectId, uuid);
            document.setExtractedTextRef(storage.put(".txt", text.getBytes(StandardCharsets.UTF_8)));
            document.setExtractionMetadata(Map.of("format", extension, "length", text.length()).toString()); document.setParsedAt(LocalDateTime.now());
            documentMapper.updateById(document); documents.transition(userId, projectId, uuid, KnowledgeDocumentStatus.INDEXING);
            indexing.index(document, text); documents.transition(userId, projectId, uuid, KnowledgeDocumentStatus.READY);
        } catch (Exception failure) {
            try { KnowledgeDocument document = documents.get(userId, projectId, uuid); if (!"DELETED".equals(document.getStatus())) documents.transition(userId, projectId, uuid, KnowledgeDocumentStatus.FAILED); } catch (Exception ignored) { }
        }
    }
}
