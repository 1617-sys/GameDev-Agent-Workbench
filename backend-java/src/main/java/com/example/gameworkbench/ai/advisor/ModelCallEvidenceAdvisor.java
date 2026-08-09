package com.example.gameworkbench.ai.advisor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

@Component
public final class ModelCallEvidenceAdvisor implements CallAdvisor {
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        long started = System.nanoTime();
        String inputDigest = digest(request.prompt().getContents());
        ChatClientResponse response = chain.nextCall(request);
        long latencyMs = (System.nanoTime() - started) / 1_000_000;
        String output = response.chatResponse() == null || response.chatResponse().getResult() == null
                ? "" : response.chatResponse().getResult().getOutput().toString();
        Map<String, Object> evidence = new LinkedHashMap<>();
        copy(request.context(), evidence, AiAdvisorContext.OPERATION, "operation");
        copy(request.context(), evidence, AiAdvisorContext.PROMPT_VERSION, "promptVersion");
        copy(request.context(), evidence, AiAdvisorContext.TRACE_ID, "traceId");
        copy(request.context(), evidence, AiAdvisorContext.PROJECT_ID, "projectId");
        copy(request.context(), evidence, AiAdvisorContext.RUN_ID, "runId");
        evidence.put("inputDigest", inputDigest);
        evidence.put("outputDigest", digest(output));
        evidence.put("latencyMs", latencyMs);
        return response.mutate().context(AiAdvisorContext.EVIDENCE, Map.copyOf(evidence)).build();
    }

    private void copy(Map<String, Object> source, Map<String, Object> target, String sourceKey, String targetKey) {
        Object value = source.get(sourceKey);
        if (value != null) target.put(targetKey, String.valueOf(value));
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    @Override public String getName() { return "gameworkbench-model-call-evidence"; }
    @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE + 120; }
}
