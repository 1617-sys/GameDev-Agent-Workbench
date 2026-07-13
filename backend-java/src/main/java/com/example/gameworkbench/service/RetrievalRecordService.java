package com.example.gameworkbench.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.gameworkbench.entity.AgentRun;
import com.example.gameworkbench.entity.RetrievalRecord;
import com.example.gameworkbench.mapper.AgentRunMapper;
import com.example.gameworkbench.mapper.RetrievalRecordMapper;
import com.example.gameworkbench.observability.ApplicationObservability;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor
public class RetrievalRecordService {
    private final RetrievalRecordMapper records; private final AgentRunMapper runs; private final ApplicationObservability observability;
    public void recordSelected(AgentRun run, JsonNode usedReferences, String queryHash) {
        if (!Boolean.TRUE.equals(run.getRagEnabled()) || usedReferences == null || !usedReferences.isArray()) return;
        for (JsonNode ref : usedReferences) {
            RetrievalRecord record = new RetrievalRecord(); record.setRetrievalUuid(UUID.randomUUID().toString());
            record.setAgentRunId(run.getId()); record.setProjectId(run.getProjectId()); record.setDocumentUuid(ref.path("document_uuid").asText());
            record.setDocumentVersion(parseInt(ref.path("document_version").asText())); record.setChunkUuid(ref.path("chunk_uuid").asText());
            record.setRankNo(ref.path("rank").asInt()); record.setScore(BigDecimal.valueOf(ref.path("score").asDouble()));
            record.setChunkingVersion(run.getChunkingVersion()); record.setEmbeddingModel(run.getEmbeddingModel()); record.setQueryHash(queryHash);
            record.setRagEnabled(true); record.setContextBudget(run.getContextBudget()); record.setMock("TRUE".equals(run.getMockState())); record.setSelectedAt(LocalDateTime.now());
            if (records.selectCount(new LambdaQueryWrapper<RetrievalRecord>().eq(RetrievalRecord::getAgentRunId, run.getId()).eq(RetrievalRecord::getChunkUuid, record.getChunkUuid())) == 0) {
                records.insert(record);
                observability.retrievalPersisted(Boolean.TRUE.equals(record.getMock()));
            }
        }
    }
    public List<RetrievalRecord> list(Long userId, Long projectId, Long agentRunId) {
        AgentRun run = runs.selectOne(new LambdaQueryWrapper<AgentRun>().eq(AgentRun::getId, agentRunId).eq(AgentRun::getUserId, userId).eq(AgentRun::getProjectId, projectId));
        if (run == null) return List.of();
        return records.selectList(new LambdaQueryWrapper<RetrievalRecord>().eq(RetrievalRecord::getAgentRunId, agentRunId).eq(RetrievalRecord::getProjectId, projectId).orderByAsc(RetrievalRecord::getRankNo));
    }
    private int parseInt(String value) { try { return Integer.parseInt(value); } catch (Exception ignored) { return 0; } }
}
