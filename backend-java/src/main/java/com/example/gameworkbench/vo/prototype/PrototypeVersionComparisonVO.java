package com.example.gameworkbench.vo.prototype;

import java.util.List;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PrototypeVersionComparisonVO {
    PrototypeVersionVO left;
    PrototypeVersionVO right;
    List<ParameterDifference> differences;

    @Value
    @Builder
    public static class ParameterDifference {
        String key;
        Object leftValue;
        Object rightValue;
        boolean changed;
    }
}
