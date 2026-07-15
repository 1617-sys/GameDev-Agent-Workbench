package com.example.gameworkbench.dto.workflow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Data;

@Data
public class AsyncWorkflowSubmitRequest {

    @NotBlank(message = "Workflow key is required")
    @Size(max = 80, message = "Workflow key length must not exceed 80")
    private String workflowKey;

    @NotBlank(message = "Game idea is required")
    @Size(max = 5000, message = "Game idea length must not exceed 5000")
    private String idea;

    @NotNull(message = "Prototype duration is required")
    @Min(value = 30, message = "Prototype duration must be at least 30 seconds")
    @Max(value = 600, message = "Prototype duration must not exceed 600 seconds")
    private Integer durationSeconds;

    @NotBlank(message = "Prototype difficulty is required")
    @Pattern(regexp = "easy|normal|hard", message = "Prototype difficulty is invalid")
    private String difficulty;

    @NotBlank(message = "Visual theme is required")
    @Size(max = 80, message = "Visual theme length must not exceed 80")
    private String visualTheme;

    @Size(max = 2000, message = "Additional requirements length must not exceed 2000")
    private String additionalRequirements;

    @Size(max = 5000, message = "Context length must not exceed 5000")
    private String context;
}
