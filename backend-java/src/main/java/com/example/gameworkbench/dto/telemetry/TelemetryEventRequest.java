package com.example.gameworkbench.dto.telemetry;

import java.util.Map;
import jakarta.validation.constraints.*;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.common.enums.ErrorCode;

@Data
public class TelemetryEventRequest {
    @NotBlank @Pattern(regexp="^[0-9a-fA-F-]{36}$") private String eventUuid;
    @NotNull @Min(1) @Max(1000) private Integer sequence;
    @NotBlank private String type;
    @NotNull @Min(0) @Max(1800000) private Integer clientElapsedMs;
    @NotNull private Map<String, Object> payload;
    @JsonAnySetter public void reject(String key, Object value) { throw new BusinessException(ErrorCode.TELEMETRY_INVALID); }
}
