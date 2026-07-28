package com.example.gameworkbench.vo.prototype;

import java.time.LocalDateTime;
import java.util.Map;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PrototypeVersionVO {
    String versionUuid;
    Integer versionNumber;
    String parentVersionUuid;
    String source;
    String lifecycleStatus;
    String directorRunUuid;
    LocalDateTime approvalUpdatedAt;
    String gameConfigArtifactUuid;
    String configDigest;
    String runtimeCapabilityVersion;
    LocalDateTime createdAt;
    Map<String, Object> parameters;
    String gameConfig;
    boolean reused;
}
