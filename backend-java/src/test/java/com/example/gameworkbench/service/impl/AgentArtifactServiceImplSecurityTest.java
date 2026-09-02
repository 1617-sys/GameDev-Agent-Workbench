package com.example.gameworkbench.service.impl;

import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.entity.AgentArtifact;
import com.example.gameworkbench.entity.GameProject;
import com.example.gameworkbench.mapper.AgentArtifactMapper;
import com.example.gameworkbench.mapper.GameProjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentArtifactServiceImplSecurityTest {

    @Test
    void rejectsForeignProjectBeforeListingArtifacts() {
        AgentArtifactMapper artifacts = mock(AgentArtifactMapper.class);
        GameProjectMapper projects = mock(GameProjectMapper.class);
        when(projects.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> new AgentArtifactServiceImpl(artifacts, projects)
                .listProjectArtifacts(99L, "owner-project"))
                .isInstanceOf(BusinessException.class);
        verify(artifacts, never()).selectList(any());
    }

    @Test
    void rejectsGuessedArtifactUuidOwnedByAnotherUser() {
        AgentArtifactMapper artifacts = mock(AgentArtifactMapper.class);
        GameProjectMapper projects = mock(GameProjectMapper.class);
        AgentArtifact artifact = AgentArtifact.builder()
                .artifactUuid("guessed-artifact")
                .projectId(7L)
                .content("owner-only-content")
                .build();
        GameProject ownerProject = new GameProject();
        ownerProject.setId(7L);
        ownerProject.setUserId(42L);
        when(artifacts.selectOne(any())).thenReturn(artifact);
        when(projects.selectById(7L)).thenReturn(ownerProject);

        assertThatThrownBy(() -> new AgentArtifactServiceImpl(artifacts, projects)
                .getArtifact(99L, "guessed-artifact"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void projectScopedDetailRejectsArtifactFromAnotherOwnedProject() {
        AgentArtifactMapper artifacts = mock(AgentArtifactMapper.class);
        GameProjectMapper projects = mock(GameProjectMapper.class);
        GameProject requestedProject = new GameProject();
        requestedProject.setId(7L); requestedProject.setUserId(42L); requestedProject.setProjectUuid("project-a");
        AgentArtifact foreignArtifact = AgentArtifact.builder()
                .artifactUuid("artifact-from-b").projectId(8L).content("must-not-leak").build();
        when(projects.selectOne(any())).thenReturn(requestedProject);
        when(artifacts.selectOne(any())).thenReturn(foreignArtifact);

        assertThatThrownBy(() -> new AgentArtifactServiceImpl(artifacts, projects)
                .getProjectArtifact(42L, "project-a", "artifact-from-b"))
                .isInstanceOf(BusinessException.class)
                .hasMessageNotContaining("must-not-leak");
    }
}
