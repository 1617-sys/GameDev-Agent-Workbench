package com.example.gameworkbench.service.impl;

import com.example.gameworkbench.client.PythonAgentClient;
import com.example.gameworkbench.client.dto.PythonAgentRequest;
import com.example.gameworkbench.client.dto.PythonAgentResponse;
import com.example.gameworkbench.common.enums.AgentRunStatus;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.dto.agent.AgentRunRequest;
import com.example.gameworkbench.entity.AgentRun;
import com.example.gameworkbench.mapper.AgentRunMapper;
import com.example.gameworkbench.service.AgentRunService;
import com.example.gameworkbench.vo.agent.AgentRunVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentRunServiceImpl implements AgentRunService {

    private final AgentRunMapper agentRunMapper;
    private final PythonAgentClient pythonAgentClient;
    private final ObjectMapper objectMapper;

    @Override
    public AgentRunVO run(Long userId, AgentRunRequest request) {
        if (userId == null) {
            throw new BusinessException(40101, "请先登录");
        }

        long startTime = System.currentTimeMillis();
        LocalDateTime now = LocalDateTime.now();

        AgentRun agentRun = new AgentRun();
        agentRun.setRunUuid(UUID.randomUUID().toString());
        agentRun.setUserId(userId);
        agentRun.setAgentType(request.getAgentType().name());
        agentRun.setInputContent(writeJsonSafely(request));
        agentRun.setStatus(AgentRunStatus.RUNNING.name());
        agentRun.setCreatedAt(now);
        agentRun.setUpdatedAt(now);
        agentRunMapper.insert(agentRun);

        try {
            PythonAgentRequest pythonRequest = PythonAgentRequest.builder()
                    .title(request.getTitle())
                    .content(request.getContent())
                    .context(request.getContext())
                    .userId(userId)
                    .build();

            PythonAgentResponse pythonResponse = pythonAgentClient.invoke(request.getAgentType(), pythonRequest);
            String outputContent = pythonResponse.getData() == null ? null : objectMapper.writeValueAsString(pythonResponse.getData());

            agentRun.setOutputContent(outputContent);
            agentRun.setErrorMessage(null);
            agentRun.setStatus(AgentRunStatus.SUCCESS.name());
            agentRun.setTimeTakenMs(System.currentTimeMillis() - startTime);
            agentRun.setUpdatedAt(LocalDateTime.now());
            agentRunMapper.updateById(agentRun);

            return toVO(agentRun);
        } catch (BusinessException exception) {
            agentRun.setStatus(AgentRunStatus.FAILED.name());
            agentRun.setErrorMessage(exception.getMessage());
            agentRun.setTimeTakenMs(System.currentTimeMillis() - startTime);
            agentRun.setUpdatedAt(LocalDateTime.now());
            agentRunMapper.updateById(agentRun);
            throw exception;
        } catch (Exception exception) {
            agentRun.setStatus(AgentRunStatus.FAILED.name());
            agentRun.setErrorMessage("Agent执行失败");
            agentRun.setTimeTakenMs(System.currentTimeMillis() - startTime);
            agentRun.setUpdatedAt(LocalDateTime.now());
            agentRunMapper.updateById(agentRun);
            throw new BusinessException(50001, "Agent执行失败");
        }
    }

    private AgentRunVO toVO(AgentRun agentRun) {
        return AgentRunVO.builder()
                .id(agentRun.getId())
                .runUuid(agentRun.getRunUuid())
                .userId(agentRun.getUserId())
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
            return String.valueOf(value);
        }
    }
}
