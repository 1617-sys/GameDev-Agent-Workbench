package com.example.gameworkbench.vo.telemetry;

import java.time.LocalDateTime;
import lombok.*;

@Data @Builder
public class PlaytestSessionVO {
    private String sessionUuid;
    private String prototypeVersionUuid;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Integer eventCount;
    private String outcome;
    private String failureReason;
    private Integer score;
    private Integer durationMs;
    private Integer hitCount;
    private Integer collectedCount;
    private Integer restartCount;
}
