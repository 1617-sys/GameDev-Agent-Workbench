package com.example.gameworkbench.service.impl;

import com.example.gameworkbench.entity.ModelCallMetric;
import com.example.gameworkbench.mapper.ModelCallMetricMapper;
import com.example.gameworkbench.service.ModelCallMetricService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ModelCallMetricServiceImpl implements ModelCallMetricService {
    private final ModelCallMetricMapper modelCallMetricMapper;

    @Override
    public void record(ModelCallMetric metric) {
        modelCallMetricMapper.insert(metric);
    }
}
