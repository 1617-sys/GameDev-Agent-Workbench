package com.example.gameworkbench.vo.demo;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameDemoStreamEventVO {

    private String stage;
    private String status;
    private String message;
    private String projectUuid;
    private String workflowRunUuid;
    private String agentRunUuid;
    private String artifactUuid;
    private String demoUrl;
    private Object data;
    private LocalDateTime eventTime;
}
