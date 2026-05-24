package com.example.gameworkbench.dto.workflow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

@Data
public class WorkflowRunRequest {

    @NotBlank(message = "项目UUID不能为空")
    private String projectUuid;

    @NotBlank(message = "工作流标题不能为空")
    @Size(max = 200, message = "工作流标题长度不能超过200")
    private String title;

    @NotBlank(message = "游戏想法不能为空")
    @Size(max = 5000, message = "游戏想法长度不能超过5000")
    private String idea;

    @Size(max = 5000, message = "补充上下文长度不能超过5000")
    private String context;
}
