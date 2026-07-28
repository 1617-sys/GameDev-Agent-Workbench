package com.example.gameworkbench.dto.prototype;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data @JsonIgnoreProperties(ignoreUnknown=false)
public class PrototypeApprovalRequest {
    @NotBlank @Pattern(regexp="APPROVED|REJECTED") private String decision;
    @NotBlank @Size(max=500) private String reason;
    @JsonAnySetter public void rejectUnknown(String field,Object value){throw new IllegalArgumentException("Unknown approval field: "+field);}
}
