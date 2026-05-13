package com.example.gameworkbench.vo.agent;

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
    private String agentType;
    private String inputContent;
    private String outputContent;
    private String status;
    private String errorMessage;
    private Long timeTakenMs;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
