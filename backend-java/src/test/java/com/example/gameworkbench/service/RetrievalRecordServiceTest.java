package com.example.gameworkbench.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.example.gameworkbench.entity.AgentRun;
import com.example.gameworkbench.entity.RetrievalRecord;
import com.example.gameworkbench.mapper.AgentRunMapper;
import com.example.gameworkbench.mapper.RetrievalRecordMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RetrievalRecordServiceTest {
    @Test void deniesCrossProjectRun() { var records = mock(RetrievalRecordMapper.class); var runs = mock(AgentRunMapper.class); assertThat(new RetrievalRecordService(records, runs, mock(com.example.gameworkbench.observability.ApplicationObservability.class)).list(1L, 2L, 3L)).isEmpty(); verify(records, never()).selectList(any()); }
    @Test void persistsOnlyActualUsedReferencesAndHashesOnlyTheQuery() throws Exception {
        var records = mock(RetrievalRecordMapper.class); when(records.selectCount(any())).thenReturn(0L);
        RetrievalRecordService service = new RetrievalRecordService(records, mock(AgentRunMapper.class), mock(com.example.gameworkbench.observability.ApplicationObservability.class));
        AgentRun run = new AgentRun(); run.setId(4L); run.setProjectId(2L); run.setRagEnabled(true); run.setContextBudget(1000); run.setChunkingVersion("v1"); run.setEmbeddingModel("fake"); run.setMockState("FALSE");
        service.recordSelected(run, new ObjectMapper().readTree("[{\"chunk_uuid\":\"c\",\"document_uuid\":\"d\",\"document_version\":\"1\",\"rank\":1,\"score\":0.8}]"), "a".repeat(64));
        ArgumentCaptor<RetrievalRecord> captured = ArgumentCaptor.forClass(RetrievalRecord.class); verify(records).insert(captured.capture());
        assertThat(captured.getValue().getChunkUuid()).isEqualTo("c"); assertThat(captured.getValue().getQueryHash()).hasSize(64);
    }
    @Test void ragOffWritesNoFakeReferences() { var records = mock(RetrievalRecordMapper.class); AgentRun run = new AgentRun(); run.setRagEnabled(false); new RetrievalRecordService(records, mock(AgentRunMapper.class), mock(com.example.gameworkbench.observability.ApplicationObservability.class)).recordSelected(run, null, "x"); verifyNoInteractions(records); }
}
