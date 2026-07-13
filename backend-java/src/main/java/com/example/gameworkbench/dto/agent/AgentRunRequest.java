package com.example.gameworkbench.dto.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.example.gameworkbench.common.enums.AgentType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRunRequest {

    @NotBlank(message = "Project UUID is required")
    private String projectUuid;

    @NotNull(message = "Agent type is required")
    private AgentType agentType;

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title length must not exceed 200")
    private String title;

    @NotBlank(message = "Content is required")
    private String content;

    @Size(max = 2000, message = "Context length must not exceed 2000")
    private String context;

    private Boolean ragEnabled;
    private Integer ragTopK;
    private Integer ragContextBudget;
}
