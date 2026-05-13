package com.example.gameworkbench.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PythonAgentResponse {

    private Integer code;
    private String message;
    private JsonNode data;

    @JsonProperty("trace_id")
    private String traceId;
}
