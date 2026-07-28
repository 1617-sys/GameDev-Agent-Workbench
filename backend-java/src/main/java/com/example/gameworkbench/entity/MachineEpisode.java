package com.example.gameworkbench.entity;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@TableName("machine_episode")
public class MachineEpisode {
    @TableId(type = IdType.AUTO) private Long id;
    private String episodeUuid;
    private Long batchId;
    private Long projectId;
    private String prototypeVersionUuid;
    private String clientEpisodeKey;
    private String sampleSource;
    private String configDigest;
    private String simulationProtocolVersion;
    private String coreVersion;
    private Long seed;
    private Integer maxSteps;
    private String observationPolicyJson;
    private String policyId;
    private String policyVersion;
    private String policyDigest;
    private String personaId;
    private String personaVersion;
    private String personaDigest;
    private String modelJson;
    private String usageJson;
    private String auditJson;
    private String timingJson;
    private String errorJson;
    private String metricVersion;
    private String executionStatus;
    private String terminationReason;
    private String outcome;
    private Integer stepCount;
    private Integer acceptedActionCount;
    private Integer invalidActionCount;
    private String finalStateHash;
    private Integer finalScore;
    private String trajectoryDigest;
    private String trajectoryRef;
    private Long wallDurationMs;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
