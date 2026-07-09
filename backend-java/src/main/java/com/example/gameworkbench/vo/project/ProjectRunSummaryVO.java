package com.example.gameworkbench.vo.project;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProjectRunSummaryVO {
    private Long projectId;
    private String projectUuid;
    private String projectName;
    private Long totalRunCount;
    private Long successRunCount;
    private Long failedRunCount;
    private LocalDateTime lastRunTime;
}
