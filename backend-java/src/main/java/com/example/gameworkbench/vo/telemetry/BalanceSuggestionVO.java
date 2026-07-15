package com.example.gameworkbench.vo.telemetry;

import java.time.LocalDateTime;
import lombok.*;

@Data @Builder
public class BalanceSuggestionVO {
    private String artifactUuid;
    private String prototypeVersionUuid;
    private String configDigest;
    private Integer sampleSize;
    private LocalDateTime snapshotAt;
    private String source;
    private String recommendation;
    private boolean reused;
}
