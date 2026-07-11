package com.example.gameworkbench.dto.workflow;

import jakarta.validation.constraints.NotBlank;
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

    @Size(max = 5000, message = "Context length must not exceed 5000")
    private String context;
}
