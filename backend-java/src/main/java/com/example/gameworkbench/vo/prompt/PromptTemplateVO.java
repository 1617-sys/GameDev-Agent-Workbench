package com.example.gameworkbench.vo.prompt;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptTemplateVO {

    private Long id;

    private String templateUuid;

    private String agentType;

    private String name;

    private String systemPrompt;

    private String userPromptTemplate;

    private Integer version;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
