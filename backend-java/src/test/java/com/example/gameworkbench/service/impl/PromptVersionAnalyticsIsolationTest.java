package com.example.gameworkbench.service.impl;

import com.example.gameworkbench.mapper.ModelCallMetricMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PromptVersionAnalyticsIsolationTest {

    @Test
    void alwaysScopesMetricQueryByAuthenticatedUserAndRequestedProject() {
        ModelCallMetricMapper mapper = mock(ModelCallMetricMapper.class);
        LocalDateTime from = LocalDateTime.of(2026, 7, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 7, 2, 0, 0);
        when(mapper.selectAnalyticsRows(99L, 7L, null, from, to)).thenReturn(List.of());

        new PromptVersionAnalyticsServiceImpl(mapper)
                .metrics(99L, 7L, null, from, to, false);

        verify(mapper).selectAnalyticsRows(99L, 7L, null, from, to);
    }
}
