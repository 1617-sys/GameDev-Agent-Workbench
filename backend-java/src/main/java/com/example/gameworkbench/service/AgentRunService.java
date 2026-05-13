package com.example.gameworkbench.service;

import com.example.gameworkbench.dto.agent.AgentRunRequest;
import com.example.gameworkbench.vo.agent.AgentRunVO;

public interface AgentRunService {

    AgentRunVO run(Long userId, AgentRunRequest request);
}
