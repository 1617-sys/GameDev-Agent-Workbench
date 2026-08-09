package com.example.gameworkbench.director.client;

import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;

public final class UnavailableDirectorDecisionClient implements DirectorDecisionClient {
    @Override
    public JsonNode decide(JsonNode snapshot, String traceId) {
        throw new BusinessException(ErrorCode.AI_MODEL_UNAVAILABLE);
    }
}
