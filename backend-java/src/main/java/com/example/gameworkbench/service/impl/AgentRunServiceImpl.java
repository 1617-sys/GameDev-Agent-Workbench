package com.example.gameworkbench.service.impl;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.gameworkbench.client.PythonAgentClient;
import com.example.gameworkbench.client.dto.PythonAgentRequest;
import com.example.gameworkbench.client.dto.PythonAgentResponse;
import com.example.gameworkbench.common.enums.AgentRunStatus;
import com.example.gameworkbench.common.enums.AgentType;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.dto.agent.AgentRunRequest;
import com.example.gameworkbench.entity.AgentRun;
import com.example.gameworkbench.entity.GameProject;
import com.example.gameworkbench.mapper.AgentRunMapper;
import com.example.gameworkbench.mapper.GameProjectMapper;
import com.example.gameworkbench.service.AgentRunService;
import com.example.gameworkbench.vo.agent.AgentRunVO;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRunServiceImpl implements AgentRunService {

    private final AgentRunMapper agentRunMapper;
    private final GameProjectMapper gameProjectMapper;
    private final PythonAgentClient pythonAgentClient;
    private final ObjectMapper objectMapper;

    @Override
    public AgentRunVO run(Long userId, AgentRunRequest request) {
        if (userId == null) {
            log.warn("[Agent] run rejected: unauthorized agentType={}", request.getAgentType());
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        GameProject gameProject = gameProjectMapper.selectOne(new LambdaQueryWrapper<GameProject>()
                .eq(GameProject::getProjectUuid, request.getProjectUuid())
                .eq(GameProject::getUserId, userId));
        if (gameProject == null) {
            log.warn("[Agent] run rejected: project not found or forbidden userId={} projectUuid={}",
                    userId, request.getProjectUuid());
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }

        long startTime = System.currentTimeMillis();
        LocalDateTime now = LocalDateTime.now();

        AgentRun agentRun = new AgentRun();
        agentRun.setRunUuid(UUID.randomUUID().toString());
        agentRun.setUserId(userId);
        agentRun.setProjectId(gameProject.getId());
        agentRun.setProjectUuid(gameProject.getProjectUuid());
        agentRun.setAgentType(request.getAgentType().name());
        agentRun.setInputContent(writeJsonSafely(request));
        agentRun.setStatus(AgentRunStatus.RUNNING.name());
        agentRun.setCreatedAt(now);
        agentRun.setUpdatedAt(now);
        agentRunMapper.insert(agentRun);

        log.info("[Agent] run started userId={} projectId={} projectUuid={} runUuid={} agentType={}",
                userId, agentRun.getProjectId(), agentRun.getProjectUuid(), agentRun.getRunUuid(),
                request.getAgentType());

        try {
            PythonAgentRequest pythonRequest = PythonAgentRequest.builder()
                    .projectUuid(request.getProjectUuid())
                    .title(request.getTitle())
                    .content(request.getContent())
                    .context(request.getContext())
                    .userId(userId)
                    .build();

            PythonAgentResponse pythonResponse = pythonAgentClient.invoke(request.getAgentType(), pythonRequest);
            String outputContent = pythonResponse.getData() == null
                    ? null
                    : objectMapper.writeValueAsString(pythonResponse.getData());

            agentRun.setOutputContent(outputContent);
            agentRun.setErrorMessage(null);
            agentRun.setStatus(AgentRunStatus.SUCCESS.name());
            agentRun.setTimeTakenMs(System.currentTimeMillis() - startTime);
            agentRun.setUpdatedAt(LocalDateTime.now());
            agentRunMapper.updateById(agentRun);

            log.info("[Agent] run succeeded userId={} projectId={} projectUuid={} runUuid={} agentType={} timeTakenMs={}",
                    userId, agentRun.getProjectId(), agentRun.getProjectUuid(), agentRun.getRunUuid(),
                    request.getAgentType(), agentRun.getTimeTakenMs());
            return toVO(agentRun);
        } catch (BusinessException exception) {
            agentRun.setStatus(AgentRunStatus.FAILED.name());
            agentRun.setErrorMessage(exception.getMessage());
            agentRun.setTimeTakenMs(System.currentTimeMillis() - startTime);
            agentRun.setUpdatedAt(LocalDateTime.now());
            agentRunMapper.updateById(agentRun);

            log.warn("[Agent] run failed userId={} projectId={} projectUuid={} runUuid={} agentType={} timeTakenMs={} message={}",
                    userId, agentRun.getProjectId(), agentRun.getProjectUuid(), agentRun.getRunUuid(),
                    request.getAgentType(), agentRun.getTimeTakenMs(), exception.getMessage());
            throw exception;
        } catch (Exception exception) {
            agentRun.setStatus(AgentRunStatus.FAILED.name());
            agentRun.setErrorMessage(ErrorCode.AGENT_RUN_ERROR.getMessage());
            agentRun.setTimeTakenMs(System.currentTimeMillis() - startTime);
            agentRun.setUpdatedAt(LocalDateTime.now());
            agentRunMapper.updateById(agentRun);

            log.error("[Agent] run exception userId={} projectId={} projectUuid={} runUuid={} agentType={} timeTakenMs={}",
                    userId, agentRun.getProjectId(), agentRun.getProjectUuid(), agentRun.getRunUuid(),
                    request.getAgentType(), agentRun.getTimeTakenMs(), exception);
            throw new BusinessException(ErrorCode.AGENT_RUN_ERROR);
        }
    }

    @Override
    public Page<AgentRunVO> listRuns(
            Long userId,
            Integer pageNum,
            Integer pageSize,
            String projectUuid,
            AgentType agentType,
            AgentRunStatus status
    ) {
        if (userId == null) {
            log.warn("[Agent] list runs rejected: unauthorized");
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        Long projectId = null;
        if (projectUuid != null && !projectUuid.isBlank()) {
            GameProject gameProject = gameProjectMapper.selectOne(new LambdaQueryWrapper<GameProject>()
                    .eq(GameProject::getProjectUuid, projectUuid)
                    .eq(GameProject::getUserId, userId));
            if (gameProject == null) {
                log.warn("[Agent] list runs rejected: project not found or forbidden userId={} projectUuid={}",
                        userId, projectUuid);
                throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
            }
            projectId = gameProject.getId();
        }

        Page<AgentRun> page = agentRunMapper.selectPage(
                new Page<>(normalizePageNum(pageNum), normalizePageSize(pageSize)),
                new LambdaQueryWrapper<AgentRun>()
                        .eq(AgentRun::getUserId, userId)
                        .eq(projectId != null, AgentRun::getProjectId, projectId)
                        .eq(agentType != null, AgentRun::getAgentType, agentType == null ? null : agentType.name())
                        .eq(status != null, AgentRun::getStatus, status == null ? null : status.name())
                        .orderByDesc(AgentRun::getCreatedAt)
        );

        Page<AgentRunVO> pageVO = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        pageVO.setRecords(page.getRecords().stream().map(this::toVO).toList());

        log.info("[AgentRun] list runs succeeded userId={} projectUuid={} agentType={} status={} pageNum={} pageSize={} total={}",
                userId, projectUuid, agentType, status, page.getCurrent(), page.getSize(), page.getTotal());
        return pageVO;
    }

    @Override
    public AgentRunVO getRun(Long userId, String runUuid) {
        if (userId == null) {
            log.warn("[Agent] get run rejected: unauthorized runUuid={}", runUuid);
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        log.info("[Agent] get run started userId={} runUuid={}", userId, runUuid);
        AgentRun agentRun = agentRunMapper.selectOne(new LambdaQueryWrapper<AgentRun>()
                .eq(AgentRun::getRunUuid, runUuid)
                .eq(AgentRun::getUserId, userId));
        if (agentRun == null) {
            log.warn("[Agent] get run rejected: run not found userId={} runUuid={}", userId, runUuid);
            throw new BusinessException(ErrorCode.AGENT_RUN_NOT_FOUND);
        }

        log.info("[Agent] get run succeeded userId={} runUuid={} status={}",
                userId, runUuid, agentRun.getStatus());
        return toVO(agentRun);
    }

    private AgentRunVO toVO(AgentRun agentRun) {
        return AgentRunVO.builder()
                .id(agentRun.getId())
                .runUuid(agentRun.getRunUuid())
                .userId(agentRun.getUserId())
                .projectId(agentRun.getProjectId())
                .projectUuid(agentRun.getProjectUuid())
                .agentType(agentRun.getAgentType())
                .inputContent(agentRun.getInputContent())
                .outputContent(agentRun.getOutputContent())
                .status(agentRun.getStatus())
                .errorMessage(agentRun.getErrorMessage())
                .timeTakenMs(agentRun.getTimeTakenMs())
                .createdAt(agentRun.getCreatedAt())
                .updatedAt(agentRun.getUpdatedAt())
                .build();
    }

    private String writeJsonSafely(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            log.warn("[Agent] input serialization failed, fallback to String.valueOf", exception);
            return String.valueOf(value);
        }
    }

    private long normalizePageNum(Integer pageNum) {
        if (pageNum == null || pageNum < 1) {
            return 1L;
        }
        return pageNum.longValue();
    }

    private long normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 10L;
        }
        return Math.min(pageSize, 100);
    }
}
