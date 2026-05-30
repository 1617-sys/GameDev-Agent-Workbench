package com.example.gameworkbench.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PythonAgentRequest {

    @JsonProperty("project_uuid")
    private String projectUuid;

    private String title;

    private String content;

    private String context;

    @JsonProperty("system_prompt")
    private String systemPrompt;

    @JsonProperty("user_prompt_template")
    private String userPromptTemplate;

    @JsonProperty("template_uuid")
    private String templateUuid;

    @JsonProperty("template_version")
    private Integer templateVersion;

    @JsonProperty("user_id")
    private Long userId;
}
