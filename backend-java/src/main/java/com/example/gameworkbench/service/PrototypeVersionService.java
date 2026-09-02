package com.example.gameworkbench.service;

import java.util.List;

import com.example.gameworkbench.dto.prototype.TunePrototypeVersionRequest;
import com.example.gameworkbench.entity.AgentArtifact;
import com.example.gameworkbench.vo.prototype.PrototypeVersionComparisonVO;
import com.example.gameworkbench.vo.prototype.PrototypeVersionVO;

public interface PrototypeVersionService {
    PrototypeVersionVO createFromArtifact(Long userId, String projectUuid, String idempotencyKey, String artifactUuid);

    void validateSourceArtifact(Long userId, String projectUuid, String artifactUuid);
    PrototypeVersionVO createFromWorkflow(Long userId, Long projectId, String workflowRunUuid, AgentArtifact artifact);
    PrototypeVersionVO tune(Long userId, String projectUuid, String parentVersionUuid, String idempotencyKey,
            TunePrototypeVersionRequest request);
    List<PrototypeVersionVO> list(Long userId, String projectUuid);
    PrototypeVersionVO get(Long userId, String projectUuid, String versionUuid);
    PrototypeVersionComparisonVO compare(Long userId, String projectUuid, String leftVersionUuid,
            String rightVersionUuid);
}
