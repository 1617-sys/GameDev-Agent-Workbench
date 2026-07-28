package com.example.gameworkbench.dto.telemetry;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.common.enums.ErrorCode;

public class CreatePlaytestSessionRequest {
    @JsonAnySetter public void reject(String key, Object value) { throw new BusinessException(ErrorCode.INVALID_PARAM); }
}
