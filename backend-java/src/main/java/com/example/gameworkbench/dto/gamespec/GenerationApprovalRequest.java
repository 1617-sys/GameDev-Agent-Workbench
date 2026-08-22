package com.example.gameworkbench.dto.gamespec;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = false)
public record GenerationApprovalRequest(
        @NotBlank @Pattern(regexp = "APPROVED|REJECTED") String decision,
        @NotBlank @Size(max = 500) String reason) {
    @JsonAnySetter
    public void rejectUnknown(String field, Object value) {
        throw new IllegalArgumentException("Unknown generation approval field: " + field);
    }
}
