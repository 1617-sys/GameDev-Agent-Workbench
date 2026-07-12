package com.example.gameworkbench.vo.agent;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRunVO {

    private Long id;
    private String runUuid;
    private Long userId;
    private Long projectId;
    private String projectUuid;
    private String agentType;
    @JsonIgnore
    private String inputContent;
    @JsonIgnore
    private String outputContent;
    private String status;
    private String errorMessage;
    private Long timeTakenMs;
    private String provider;
    private String modelName;
    private String mockState;
    private String traceId;
    private String errorCategory;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
