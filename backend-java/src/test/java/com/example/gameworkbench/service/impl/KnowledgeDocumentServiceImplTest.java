package com.example.gameworkbench.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.gameworkbench.common.enums.KnowledgeDocumentStatus;
import com.example.gameworkbench.common.enums.KnowledgeSourceType;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.entity.GameProject;
import com.example.gameworkbench.entity.KnowledgeDocument;
import com.example.gameworkbench.mapper.GameProjectMapper;
import com.example.gameworkbench.mapper.KnowledgeDocumentMapper;

@ExtendWith(MockitoExtension.class)
class KnowledgeDocumentServiceImplTest {
    @Mock private GameProjectMapper gameProjectMapper;
    @Mock private KnowledgeDocumentMapper knowledgeDocumentMapper;
    private KnowledgeDocumentServiceImpl service;

    @BeforeEach void setUp() {
        service = new KnowledgeDocumentServiceImpl(gameProjectMapper, knowledgeDocumentMapper);
        when(gameProjectMapper.selectOne(any())).thenReturn(project(7L));
    }

    @Test void reusesTheExistingDocumentForTheSameProjectAndHash() {
        KnowledgeDocument existing = document("existing", "READY");
        when(knowledgeDocumentMapper.selectActiveByHashAndProject(7L, hash())).thenReturn(existing);

        assertThat(service.create(3L, 7L, "rules.md", KnowledgeSourceType.UPLOAD, hash(), "safe/key")).isSameAs(existing);
        verify(knowledgeDocumentMapper, never()).insert(any(KnowledgeDocument.class));
    }

    @Test void allowsTheSameHashInAnotherProjectOnlyAfterThatProjectIsAuthorized() {
        when(gameProjectMapper.selectOne(any())).thenReturn(project(8L));
        when(knowledgeDocumentMapper.selectLatestVersionForUpdate(8L)).thenReturn(0);

        service.create(3L, 8L, "rules.md", KnowledgeSourceType.UPLOAD, hash(), "safe/key");
        ArgumentCaptor<KnowledgeDocument> captured = ArgumentCaptor.forClass(KnowledgeDocument.class);
        verify(knowledgeDocumentMapper).insert(captured.capture());
        assertThat(captured.getValue()).extracting(KnowledgeDocument::getProjectId, KnowledgeDocument::getVersion,
                KnowledgeDocument::getStatus).containsExactly(8L, 1, "UPLOADED");
    }

    @Test void preventsCrossProjectUuidReadsAtTheRepositoryBoundary() {
        when(knowledgeDocumentMapper.selectActiveByUuidAndProject(7L, "doc")).thenReturn(null);
        assertThatThrownBy(() -> service.get(3L, 7L, "doc")).isInstanceOf(BusinessException.class);
        verify(knowledgeDocumentMapper).selectActiveByUuidAndProject(7L, "doc");
    }

    @Test void rejectsIllegalStatusTransitionsAndSoftDeletesLegalOnes() {
        KnowledgeDocument uploaded = document("doc", "UPLOADED");
        when(knowledgeDocumentMapper.selectActiveByUuidAndProject(7L, "doc")).thenReturn(uploaded);
        assertThatThrownBy(() -> service.transition(3L, 7L, "doc", KnowledgeDocumentStatus.READY))
                .isInstanceOf(BusinessException.class);

        service.delete(3L, 7L, "doc");
        assertThat(uploaded.getStatus()).isEqualTo("DELETED");
        assertThat(uploaded.getDeleted()).isEqualTo(1);
        assertThat(uploaded.getDeletedAt()).isNotNull();
        verify(knowledgeDocumentMapper).updateById(uploaded);
    }

    @Test void rejectsUnauthorizedProjectBeforeAnyDocumentQuery() {
        when(gameProjectMapper.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> service.list(3L, 7L)).isInstanceOf(BusinessException.class);
        verify(knowledgeDocumentMapper, never()).selectActiveByProject(any());
    }

    private static GameProject project(Long id) { GameProject project = new GameProject(); project.setId(id); project.setUserId(3L); return project; }
    private static KnowledgeDocument document(String uuid, String status) { return KnowledgeDocument.builder().id(1L).documentUuid(uuid).projectId(7L).status(status).build(); }
    private static String hash() { return "a".repeat(64); }
}
