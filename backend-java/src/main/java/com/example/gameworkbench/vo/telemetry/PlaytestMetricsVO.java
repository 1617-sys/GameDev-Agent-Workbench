package com.example.gameworkbench.vo.telemetry;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.*;

@Data @Builder
public class PlaytestMetricsVO {
    private String prototypeVersionUuid;
    private Integer sampleSize;
    private boolean sufficientForAi;
    private Double winRate;
    private Long averageDurationMs;
    private Long averageScore;
    private Double averageHitCount;
    private Double averageCollectedCount;
    private Double averageRestartCount;
    private Map<String, Integer> failures;
    private LocalDateTime snapshotAt;
}
