package com.example.gameworkbench.service.impl;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.config.WorkflowRateLimitProperties;
import com.example.gameworkbench.mapper.WorkflowRunMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class WorkflowSubmissionGateImplTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private WorkflowRunMapper workflowRuns;

    private WorkflowSubmissionGateImpl gate;

    @BeforeEach
    void setUp() {
        gate = new WorkflowSubmissionGateImpl(redisTemplate, workflowRuns,
                new WorkflowRateLimitProperties("v1", 5, 60, 60, 100));
    }

    @Test
    void allowsTheFirstSubmissionUsingStringLuaArguments() {
        when(redisTemplate.execute(any(), anyList(), any(), any())).thenReturn(1L);
        when(workflowRuns.countNonTerminalRuns()).thenReturn(0L);

        assertThatCode(() -> gate.checkNewSubmission(7L)).doesNotThrowAnyException();

        verify(redisTemplate).execute(any(), eq(java.util.List.of("workflow:submit:rate:v1:user:7")), eq("60"), eq("5"));
    }

    @Test
    void preservesRateLimitAndRedisUnavailableFailures() {
        when(redisTemplate.execute(any(), anyList(), any(), any())).thenReturn(0L);
        assertThatThrownBy(() -> gate.checkNewSubmission(7L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.WORKFLOW_RATE_LIMITED.getMessage());

        when(redisTemplate.execute(any(), anyList(), any(), any())).thenThrow(new IllegalStateException("redis down"));
        assertThatThrownBy(() -> gate.checkNewSubmission(8L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.WORKFLOW_RATE_LIMIT_UNAVAILABLE.getMessage());
    }
}
