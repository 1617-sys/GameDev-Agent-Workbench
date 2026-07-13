package com.example.gameworkbench.service.impl;

import com.example.gameworkbench.entity.ModelCallMetric;
import com.example.gameworkbench.mapper.ModelCallMetricMapper;
import com.example.gameworkbench.observability.ApplicationObservability;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ModelCallMetricServiceImplTest {
    @Test
    void persistsEachMetricAsAnIndependentRecord() {
        ModelCallMetricMapper mapper = mock(ModelCallMetricMapper.class);
        ApplicationObservability observability = mock(ApplicationObservability.class);
        ModelCallMetric metric = new ModelCallMetric();
        metric.setAgentRunId(42L);

        new ModelCallMetricServiceImpl(mapper, observability).record(metric);

        verify(mapper).insert(metric);
        verify(observability).modelCallPersisted(metric);
    }
}
