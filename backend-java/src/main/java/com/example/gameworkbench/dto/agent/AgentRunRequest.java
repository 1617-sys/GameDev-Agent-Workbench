package com.example.gameworkbench.dto.agent;

import com.example.gameworkbench.common.enums.AgentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AgentRunRequest {

    @NotNull(message = "Agent类型不能为空")
    private AgentType agentType;

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题长度不能超过200")
    private String title;

    @NotBlank(message = "内容不能为空")
    private String content;

    @Size(max = 2000, message = "上下文长度不能超过2000")
    private String context;
}
