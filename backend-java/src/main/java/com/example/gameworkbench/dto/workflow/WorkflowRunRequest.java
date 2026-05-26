package com.example.gameworkbench.dto.workflow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

@Data
public class WorkflowRunRequest {

    @NotBlank(message = "Project UUID is required")
    private String projectUuid;

    @NotBlank(message = "Workflow title is required")
    @Size(max = 200, message = "Workflow title length must not exceed 200")
    private String title;

    @NotBlank(message = "Game idea is required")
    @Size(max = 5000, message = "Game idea length must not exceed 5000")
    private String idea;

    @Size(max = 5000, message = "Context length must not exceed 5000")
    private String context;
}
