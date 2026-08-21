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

/**
 * 工作流提交入口的容量保护。
 *
 * <p>Redis Lua 脚本实现按用户的固定窗口限流，数据库计数用于限制系统级未完成任务积压。
 * Redis 不可用时选择 fail-closed：拒绝新提交，而不是绕过限流继续压垮下游。</p>
 *
 * <p>这是固定窗口算法，窗口交界处可能出现突发流量；当前目标是单机工程保护，
 * 不是精确的分布式配额系统。</p>
 */
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
            // WHY: INCR 与首次 EXPIRE 必须原子执行，否则并发或进程中断可能留下无过期键。
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
