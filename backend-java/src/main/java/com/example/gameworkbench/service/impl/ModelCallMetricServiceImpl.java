package com.example.gameworkbench.service.impl;

import com.example.gameworkbench.entity.ModelCallMetric;
import com.example.gameworkbench.mapper.ModelCallMetricMapper;
import com.example.gameworkbench.observability.ApplicationObservability;
import com.example.gameworkbench.service.ModelCallMetricService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ModelCallMetricServiceImpl implements ModelCallMetricService {
    private final ModelCallMetricMapper modelCallMetricMapper;
    private final ApplicationObservability observability;

    @Override
    public void record(ModelCallMetric metric) {
        modelCallMetricMapper.insert(metric);
        observability.modelCallPersisted(metric);
    }
}
