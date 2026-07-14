package com.example.gameworkbench.service.impl;

import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.config.WorkflowRateLimitProperties;
import com.example.gameworkbench.mapper.WorkflowRunMapper;
import com.example.gameworkbench.service.WorkflowSubmissionGate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowSubmissionGateImpl implements WorkflowSubmissionGate {
    private static final DefaultRedisScript<Long> FIXED_WINDOW_SCRIPT = new DefaultRedisScript<>(
            "local current=redis.call('incr',KEYS[1]); if current==1 then redis.call('expire',KEYS[1],ARGV[1]); end; "
                    + "if current<=tonumber(ARGV[2]) then return 1 else return 0 end", Long.class);
    private final StringRedisTemplate redisTemplate;
    private final WorkflowRunMapper workflowRunMapper;
    private final WorkflowRateLimitProperties properties;

    @Override
    public void checkNewSubmission(Long userId) {
        String key = "workflow:submit:rate:" + properties.policyVersion() + ":user:" + userId;
        try {
            Long allowed = redisTemplate.execute(FIXED_WINDOW_SCRIPT, List.of(key),
                    Long.toString(properties.windowSeconds()), Integer.toString(properties.maxSubmissionsPerWindow()));
            if (!Long.valueOf(1L).equals(allowed)) throw new BusinessException(ErrorCode.WORKFLOW_RATE_LIMITED);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("[WorkflowRateLimit] redis unavailable userId={} policy={}", userId, properties.policyVersion());
            throw new BusinessException(ErrorCode.WORKFLOW_RATE_LIMIT_UNAVAILABLE);
        }
        if (workflowRunMapper.countNonTerminalRuns() >= properties.maxPendingRuns()) {
            log.warn("[WorkflowRateLimit] backlog gate rejected userId={} maxPendingRuns={}", userId, properties.maxPendingRuns());
            throw new BusinessException(ErrorCode.WORKFLOW_BACKPRESSURE);
        }
    }
}
