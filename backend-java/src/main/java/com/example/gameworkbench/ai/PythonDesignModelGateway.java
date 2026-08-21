package com.example.gameworkbench.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import com.example.gameworkbench.client.PythonAgentClient;
import com.example.gameworkbench.client.dto.PythonAgentRequest;
import com.example.gameworkbench.client.dto.PythonAgentResponse;
import com.example.gameworkbench.common.enums.AgentType;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "python")
public class PythonDesignModelGateway implements DesignModelGateway {
    private final PythonAgentClient client;

    @Override
    public PythonAgentResponse invoke(AgentType agentType, PythonAgentRequest request) {
        return client.invoke(agentType, request);
    }
}
