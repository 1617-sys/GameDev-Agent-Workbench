package com.example.gameworkbench.vo.player;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value @Builder
public class PlayerRunVO {
    String runUuid;
    String prototypeVersionUuid;
    String clientBatchKey;
    String status;
    String persistedBatchUuid;
    int attempt;
    String errorCode;
    String errorMessage;
    LocalDateTime createdAt;
    LocalDateTime completedAt;
    boolean reused;
}
