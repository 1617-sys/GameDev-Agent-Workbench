package com.example.gameworkbench.vo.telemetry;

import lombok.*;

@Data @Builder
public class PlaytestMetricsComparisonVO {
    private PlaytestMetricsVO left;
    private PlaytestMetricsVO right;
}
