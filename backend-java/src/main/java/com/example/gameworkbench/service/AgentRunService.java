package com.example.gameworkbench.service;

import java.util.List;

import com.example.gameworkbench.dto.agent.AgentRunRequest;
import com.example.gameworkbench.vo.agent.AgentRunVO;

public interface AgentRunService {

    AgentRunVO run(Long userId, AgentRunRequest request);

    AgentRunVO getRun(Long userId, String runUuid);

    List<AgentRunVO> listRuns(Long userId);
}
