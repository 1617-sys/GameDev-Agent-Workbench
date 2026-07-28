package com.example.gameworkbench.dto.prototype;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonAnySetter;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = false)
public class CreatePrototypeVersionRequest {
    @NotBlank(message = "GameConfig artifact UUID is required")
    private String artifactUuid;

    @JsonAnySetter
    public void rejectUnknown(String field, Object value) {
        throw new IllegalArgumentException("Unknown field: " + field);
    }
}
