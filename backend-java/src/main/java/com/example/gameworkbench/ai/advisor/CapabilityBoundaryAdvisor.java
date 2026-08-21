package com.example.gameworkbench.ai.advisor;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public final class CapabilityBoundaryAdvisor implements CallAdvisor {
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        Object value = request.context().get(AiAdvisorContext.CAPABILITY_BOUNDARY);
        if (!(value instanceof String boundary) || !StringUtils.hasText(boundary)) return chain.nextCall(request);
        String addition = """

                AUTHORITATIVE CAPABILITY BOUNDARY (closed world):
                <capability-boundary>
                %s
                </capability-boundary>
                Never invent capabilities, fields, tools, or parameter values outside this boundary.
                """.formatted(boundary);
        return chain.nextCall(request.mutate().prompt(request.prompt().augmentSystemMessage(message ->
                message.mutate().text(message.getText() + addition).build())).build());
    }

    @Override public String getName() { return "gameworkbench-capability-boundary"; }
    @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE + 110; }
}
