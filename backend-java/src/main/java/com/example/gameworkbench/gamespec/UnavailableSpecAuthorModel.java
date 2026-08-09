package com.example.gameworkbench.gamespec;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;

@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "python")
public class UnavailableSpecAuthorModel implements SpecAuthorModel {
    @Override
    public SpecAuthorModelResponse author(SpecAuthorModelRequest request) {
        throw new BusinessException(ErrorCode.AI_MODEL_UNAVAILABLE);
    }
}
