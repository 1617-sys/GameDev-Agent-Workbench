package com.example.gameworkbench.dto.promptTemplate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PromptTemplateRequest {

    private String templateUuid;

    private String projectUuid;

    @NotBlank(message = "Agent type is required")
    private String agentType;

    @NotBlank(message = "System prompt is required")
    private String systemPrompt;

    @NotBlank(message = "User prompt template is required")
    private String userPromptTemplate;

    @NotNull(message = "Version is required")
    private Integer version;

    @NotBlank(message = "Name is required")
    private String name;

    private String status;
}
