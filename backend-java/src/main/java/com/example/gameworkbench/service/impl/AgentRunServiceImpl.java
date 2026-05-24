package com.example.gameworkbench.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.gameworkbench.client.PythonAgentClient;
import com.example.gameworkbench.client.dto.PythonAgentRequest;
import com.example.gameworkbench.client.dto.PythonAgentResponse;
import com.example.gameworkbench.common.enums.AgentRunStatus;
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
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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
            log.warn("[Agent] 执行失败：未登录请求 agentType={}", request.getAgentType());
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        GameProject gameProject = gameProjectMapper.selectOne(new LambdaQueryWrapper<GameProject>()
                .eq(GameProject::getProjectUuid, request.getProjectUuid())
                .eq(GameProject::getUserId, userId));
        if (gameProject == null) {
            log.warn("[Agent] 执行失败：项目不存在或无权访问 userId={} projectUuid={}",
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

        log.info("[Agent] 执行开始 userId={} projectId={} projectUuid={} runUuid={} agentType={}",
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

            log.info("[Agent] 执行成功 userId={} projectId={} projectUuid={} runUuid={} agentType={} timeTakenMs={}",
                    userId, agentRun.getProjectId(), agentRun.getProjectUuid(), agentRun.getRunUuid(),
                    request.getAgentType(), agentRun.getTimeTakenMs());
            return toVO(agentRun);
        } catch (BusinessException exception) {
            agentRun.setStatus(AgentRunStatus.FAILED.name());
            agentRun.setErrorMessage(exception.getMessage());
            agentRun.setTimeTakenMs(System.currentTimeMillis() - startTime);
            agentRun.setUpdatedAt(LocalDateTime.now());
            agentRunMapper.updateById(agentRun);

            log.warn("[Agent] 执行失败 userId={} projectId={} projectUuid={} runUuid={} agentType={} timeTakenMs={} message={}",
                    userId, agentRun.getProjectId(), agentRun.getProjectUuid(), agentRun.getRunUuid(),
                    request.getAgentType(), agentRun.getTimeTakenMs(), exception.getMessage());
            throw exception;
        } catch (Exception exception) {
            agentRun.setStatus(AgentRunStatus.FAILED.name());
            agentRun.setErrorMessage(ErrorCode.AGENT_RUN_ERROR.getMessage());
            agentRun.setTimeTakenMs(System.currentTimeMillis() - startTime);
            agentRun.setUpdatedAt(LocalDateTime.now());
            agentRunMapper.updateById(agentRun);

            log.error("[Agent] 执行异常 userId={} projectId={} projectUuid={} runUuid={} agentType={} timeTakenMs={}",
                    userId, agentRun.getProjectId(), agentRun.getProjectUuid(), agentRun.getRunUuid(),
                    request.getAgentType(), agentRun.getTimeTakenMs(), exception);
            throw new BusinessException(ErrorCode.AGENT_RUN_ERROR);
        }
    }

    @Override
    public List<AgentRunVO> listRuns(Long userId) {
        if (userId == null) {
            log.warn("[Agent] 查询执行记录列表失败：未登录请求");
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        log.info("[Agent] 查询执行记录列表开始 userId={}", userId);
        List<AgentRun> runs = agentRunMapper.selectList(new LambdaQueryWrapper<AgentRun>()
                .eq(AgentRun::getUserId, userId)
                .orderByDesc(AgentRun::getCreatedAt));
        log.info("[Agent] 查询执行记录列表成功 userId={} count={}", userId, runs.size());
        return runs.stream().map(this::toVO).toList();
    }

    @Override
    public AgentRunVO getRun(Long userId, String runUuid) {
        if (userId == null) {
            log.warn("[Agent] 查询执行记录失败：未登录请求 runUuid={}", runUuid);
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        log.info("[Agent] 查询执行记录开始 userId={} runUuid={}", userId, runUuid);
        AgentRun agentRun = agentRunMapper.selectOne(new LambdaQueryWrapper<AgentRun>()
                .eq(AgentRun::getRunUuid, runUuid)
                .eq(AgentRun::getUserId, userId));
        if (agentRun == null) {
            log.warn("[Agent] 查询执行记录失败：记录不存在 userId={} runUuid={}", userId, runUuid);
            throw new BusinessException(ErrorCode.AGENT_RUN_NOT_FOUND);
        }

        log.info("[Agent] 查询执行记录成功 userId={} runUuid={} status={}",
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
            log.warn("[Agent] 执行记录输入序列化失败，改用 String.valueOf 兜底", exception);
            return String.valueOf(value);
        }
    }
}
