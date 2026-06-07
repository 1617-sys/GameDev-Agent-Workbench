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
public class GameBuildResponse {

    private String status;

    private String title;

    private String content;

    private String message;

    @JsonProperty("demo_url")
    private String demoUrl;

    @JsonProperty("build_id")
    private String buildId;

    @JsonProperty("time_taken_ms")
    private Long timeTakenMs;

    @JsonProperty("error_message")
    private String errorMessage;
}
