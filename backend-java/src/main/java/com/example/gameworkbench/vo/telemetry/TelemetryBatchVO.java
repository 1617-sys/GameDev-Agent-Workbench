package com.example.gameworkbench.vo.telemetry;

import lombok.*;

@Data @Builder
public class TelemetryBatchVO {
    private String batchUuid;
    private Integer acceptedCount;
    private boolean reused;
    private PlaytestSessionVO session;
}
