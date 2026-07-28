package com.example.gameworkbench.dto.director;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data @JsonIgnoreProperties(ignoreUnknown=false)
public class SubmitDirectorRunRequest {
    @NotNull private JsonNode goal;
    @NotNull private JsonNode budget;
    private JsonNode facts;
    @JsonAnySetter public void rejectUnknown(String field,Object value){throw new IllegalArgumentException("Unknown Director field: "+field);}
}
