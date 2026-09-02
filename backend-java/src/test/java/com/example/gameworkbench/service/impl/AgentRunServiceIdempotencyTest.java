package com.example.gameworkbench.service.impl;

import com.example.gameworkbench.ai.DesignModelGateway;
import com.example.gameworkbench.common.enums.AgentType;
import com.example.gameworkbench.dto.agent.AgentRunRequest;
import com.example.gameworkbench.entity.AgentRun;
import com.example.gameworkbench.entity.GameProject;
import com.example.gameworkbench.mapper.*;
import com.example.gameworkbench.observability.ApplicationObservability;
import com.example.gameworkbench.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentRunServiceIdempotencyTest {
    @Test
    void concurrentDuplicateInsertReloadsTheWinnerInsteadOfReturning500() {
        AgentRunMapper runs = mock(AgentRunMapper.class);
        GameProjectMapper projects = mock(GameProjectMapper.class);
        ObjectMapper json = new ObjectMapper();
        AgentRunRequest request = AgentRunRequest.builder().projectUuid("project-1").agentType(AgentType.GAME_CONCEPT)
                .title("title").content("content").build();
        AgentRun winner = new AgentRun(); winner.setRunUuid("winner"); winner.setRequestFingerprint(sha256(write(json, request)));
        when(runs.selectOne(any())).thenReturn(null, winner);
        doThrow(new DuplicateKeyException("race")).when(runs).insert(any(AgentRun.class));
        GameProject project = new GameProject(); project.setId(9L); project.setProjectUuid("project-1");
        when(projects.selectOne(any())).thenReturn(project);
        AgentRunServiceImpl service = new AgentRunServiceImpl(runs, projects, mock(DesignModelGateway.class),
                json, mock(PromptTemplateMapper.class), mock(PromptVersionMapper.class),
                mock(ModelCallMetricService.class), mock(RetrievalService.class), mock(RetrievalRecordService.class),
                mock(KnowledgeStorage.class), mock(EmbeddingProvider.class), mock(ApplicationObservability.class));
        assertThat(service.run(7L, request, "same-key").getRunUuid()).isEqualTo("winner");
    }

    private static String write(ObjectMapper json, Object value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new RuntimeException(e); } }
    private static String sha256(String value) { try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); } catch (Exception e) { throw new RuntimeException(e); } }
}
