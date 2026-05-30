package com.example.gameworkbench.dto.promptTemplate;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PromptTemplateRequest {
    @NotBlank(message = "Template UUID is required")
    private String templateUuid;

    @NotBlank(message = "Project UUID is required")
    private String projectUuid;

    @NotBlank(message = "Agent type is required")
    private String agentType;

    @NotBlank(message = "System prompt is required")
    private String systemPrompt;

    @NotBlank
    private String userPrompt;

    @NotBlank(message = "Version is required")
    private Integer version;

    @NotBlank(message = "Name is required")
    private String name;
}
