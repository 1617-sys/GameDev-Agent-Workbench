package com.example.gameworkbench.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.dto.knowledge.KnowledgeLibraryResponse;
import com.example.gameworkbench.entity.GameProject;
import com.example.gameworkbench.entity.KnowledgeDocument;
import com.example.gameworkbench.mapper.GameProjectMapper;
import com.example.gameworkbench.mapper.KnowledgeDocumentMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class KnowledgeDocumentReadService {

    private final GameProjectMapper projects;
    private final KnowledgeDocumentMapper documents;

    public KnowledgeLibraryResponse list(Long userId, String projectUuid) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        GameProject project = projects.selectOne(new LambdaQueryWrapper<GameProject>()
                .eq(GameProject::getUserId, userId)
                .eq(GameProject::getProjectUuid, projectUuid));
        if (project == null) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }
        List<KnowledgeLibraryResponse.KnowledgeDocumentSummary> summaries = documents
                .selectActiveByProject(project.getId()).stream()
                .map(this::toSummary)
                .toList();
        return new KnowledgeLibraryResponse(
                summaries,
                new KnowledgeLibraryResponse.Capabilities(true, false, false)
        );
    }

    private KnowledgeLibraryResponse.KnowledgeDocumentSummary toSummary(KnowledgeDocument document) {
        return new KnowledgeLibraryResponse.KnowledgeDocumentSummary(
                document.getDocumentUuid(),
                document.getName(),
                document.getSourceType(),
                summarizeHash(document.getContentHash()),
                document.getVersion(),
                document.getStatus(),
                "FAILED".equals(document.getStatus()) ? "解析或索引失败；请检查文件类型和限制后重试。" : null,
                document.getParsedAt(),
                document.getIndexedAt(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }

    private String summarizeHash(String hash) {
        return hash == null ? null : hash.substring(0, Math.min(hash.length(), 12));
    }
}
