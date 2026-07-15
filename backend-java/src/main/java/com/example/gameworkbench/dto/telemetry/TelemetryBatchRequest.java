package com.example.gameworkbench.dto.telemetry;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.common.enums.ErrorCode;

@Data
public class TelemetryBatchRequest {
    @NotBlank @Pattern(regexp="^[0-9a-fA-F-]{36}$") private String batchUuid;
    @NotEmpty @Size(max=50) private List<@Valid TelemetryEventRequest> events;
    @JsonAnySetter public void reject(String key, Object value) { throw new BusinessException(ErrorCode.TELEMETRY_INVALID); }
}
