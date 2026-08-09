package com.example.gameworkbench.ai;

import com.example.gameworkbench.client.dto.PythonAgentRequest;
import com.example.gameworkbench.client.dto.PythonAgentResponse;
import com.example.gameworkbench.common.enums.AgentType;

/** Provider-neutral port for one-shot design model calls. */
public interface DesignModelGateway {
    PythonAgentResponse invoke(AgentType agentType, PythonAgentRequest request);
}
