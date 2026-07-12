package com.example.gameworkbench.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.enums.KnowledgeDocumentStatus;
import com.example.gameworkbench.common.enums.KnowledgeSourceType;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.entity.GameProject;
import com.example.gameworkbench.entity.KnowledgeDocument;
import com.example.gameworkbench.mapper.GameProjectMapper;
import com.example.gameworkbench.mapper.KnowledgeDocumentMapper;
import com.example.gameworkbench.service.KnowledgeDocumentService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class KnowledgeDocumentServiceImpl implements KnowledgeDocumentService {
    private final GameProjectMapper gameProjectMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Override
    @Transactional
    public KnowledgeDocument create(Long userId, Long projectId, String name, KnowledgeSourceType sourceType,
            String contentHash, String storageRef) {
        requireProject(userId, projectId);
        validateCreate(name, sourceType, contentHash);
        KnowledgeDocument existing = knowledgeDocumentMapper.selectActiveByHashAndProject(projectId, contentHash);
        if (existing != null) {
            return existing;
        }
        int version = knowledgeDocumentMapper.selectLatestVersionForUpdate(projectId) + 1;
        LocalDateTime now = LocalDateTime.now();
        KnowledgeDocument document = KnowledgeDocument.builder()
                .documentUuid(UUID.randomUUID().toString()).projectId(projectId).name(name.trim())
                .sourceType(sourceType.name()).contentHash(contentHash.toLowerCase()).version(version)
                .status(KnowledgeDocumentStatus.UPLOADED.name()).storageRef(storageRef)
                .createdAt(now).updatedAt(now).deleted(0).build();
        knowledgeDocumentMapper.insert(document);
        return document;
    }

    @Override
    public KnowledgeDocument get(Long userId, Long projectId, String documentUuid) {
        requireProject(userId, projectId);
        return requireDocument(projectId, documentUuid);
    }

    @Override
    public List<KnowledgeDocument> list(Long userId, Long projectId) {
        requireProject(userId, projectId);
        return knowledgeDocumentMapper.selectActiveByProject(projectId);
    }

    @Override
    @Transactional
    public KnowledgeDocument transition(Long userId, Long projectId, String documentUuid, KnowledgeDocumentStatus target) {
        requireProject(userId, projectId);
        if (target == null) throw new BusinessException(ErrorCode.INVALID_PARAM);
        KnowledgeDocument document = requireDocument(projectId, documentUuid);
        KnowledgeDocumentStatus current = KnowledgeDocumentStatus.valueOf(document.getStatus());
        if (!current.canTransitionTo(target)) throw new BusinessException(ErrorCode.INVALID_PARAM);
        document.setStatus(target.name());
        document.setUpdatedAt(LocalDateTime.now());
        knowledgeDocumentMapper.updateById(document);
        return document;
    }

    @Override
    public KnowledgeDocument invalidate(Long userId, Long projectId, String documentUuid) {
        return transition(userId, projectId, documentUuid, KnowledgeDocumentStatus.INVALID);
    }

    @Override
    @Transactional
    public void delete(Long userId, Long projectId, String documentUuid) {
        requireProject(userId, projectId);
        KnowledgeDocument document = requireDocument(projectId, documentUuid);
        KnowledgeDocumentStatus current = KnowledgeDocumentStatus.valueOf(document.getStatus());
        if (!current.canTransitionTo(KnowledgeDocumentStatus.DELETED)) throw new BusinessException(ErrorCode.INVALID_PARAM);
        document.setStatus(KnowledgeDocumentStatus.DELETED.name());
        document.setDeletedAt(LocalDateTime.now());
        document.setDeleted(1);
        document.setUpdatedAt(LocalDateTime.now());
        knowledgeDocumentMapper.updateById(document);
    }

    private GameProject requireProject(Long userId, Long projectId) {
        if (userId == null || projectId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        GameProject project = gameProjectMapper.selectOne(new LambdaQueryWrapper<GameProject>()
                .eq(GameProject::getId, projectId).eq(GameProject::getUserId, userId));
        if (project == null) throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        return project;
    }

    private KnowledgeDocument requireDocument(Long projectId, String documentUuid) {
        if (!StringUtils.hasText(documentUuid)) throw new BusinessException(ErrorCode.INVALID_PARAM);
        KnowledgeDocument document = knowledgeDocumentMapper.selectActiveByUuidAndProject(projectId, documentUuid);
        if (document == null) throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        return document;
    }

    private void validateCreate(String name, KnowledgeSourceType sourceType, String contentHash) {
        if (!StringUtils.hasText(name) || name.length() > 255 || name.chars().anyMatch(c -> Character.isISOControl((char) c))
                || sourceType == null || contentHash == null || !contentHash.matches("(?i)[0-9a-f]{64}")) {
            throw new BusinessException(ErrorCode.INVALID_PARAM);
        }
    }
}
