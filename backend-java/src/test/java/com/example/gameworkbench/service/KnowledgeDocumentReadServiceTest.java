package com.example.gameworkbench.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.entity.GameProject;
import com.example.gameworkbench.entity.KnowledgeDocument;
import com.example.gameworkbench.mapper.GameProjectMapper;
import com.example.gameworkbench.mapper.KnowledgeDocumentMapper;

class KnowledgeDocumentReadServiceTest {

    @Test
    void returnsOnlySafeSummaryFieldsForOwnedProject() {
        GameProjectMapper projects = mock(GameProjectMapper.class);
        KnowledgeDocumentMapper documents = mock(KnowledgeDocumentMapper.class);
        GameProject project = new GameProject();
        project.setId(11L);
        KnowledgeDocument document = KnowledgeDocument.builder()
                .documentUuid("document-1")
                .name("guide.md")
                .contentHash("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
                .status("FAILED")
                .failureReason("C:\\secret\\document.txt token=should-not-leak")
                .storageRef("private-storage-key")
                .build();
        when(projects.selectOne(any())).thenReturn(project);
        when(documents.selectActiveByProject(11L)).thenReturn(List.of(document));

        var result = new KnowledgeDocumentReadService(projects, documents).list(7L, "project-1");

        assertThat(result.documents()).singleElement().satisfies(summary -> {
            assertThat(summary.contentHashSummary()).isEqualTo("0123456789ab");
            assertThat(summary.failureSummary()).doesNotContain("secret", "token", "storage");
        });
        assertThat(result.capabilities().upload()).isTrue();
        assertThat(result.capabilities().invalidate()).isFalse();
    }

    @Test
    void refusesForeignProjectBeforeReadingDocuments() {
        GameProjectMapper projects = mock(GameProjectMapper.class);
        KnowledgeDocumentMapper documents = mock(KnowledgeDocumentMapper.class);
        when(projects.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> new KnowledgeDocumentReadService(projects, documents)
                .list(7L, "foreign-project"))
                .isInstanceOf(BusinessException.class);
        verify(documents, never()).selectActiveByProject(any());
    }
}
