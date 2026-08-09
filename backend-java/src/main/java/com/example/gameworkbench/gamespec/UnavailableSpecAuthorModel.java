package com.example.gameworkbench.gamespec;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "python")
public class UnavailableSpecAuthorModel implements SpecAuthorModel {
    @Override
    public ObjectNode author(String idea, ObjectNode currentSpec, String diagnostics) {
        throw new BusinessException(ErrorCode.AI_MODEL_UNAVAILABLE);
    }
}
