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

    private String title;
    private String content;
    private String context;

    @JsonProperty("user_id")
    private Long userId;
}
