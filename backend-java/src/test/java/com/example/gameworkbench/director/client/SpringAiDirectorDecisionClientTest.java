package com.example.gameworkbench.director.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import com.example.gameworkbench.ai.advisor.CapabilityBoundaryAdvisor;
import com.example.gameworkbench.ai.advisor.ModelCallEvidenceAdvisor;
import com.example.gameworkbench.ai.advisor.ProjectContextAdvisor;
import com.example.gameworkbench.director.ai.SpringAiDirectorTools;
import com.example.gameworkbench.director.tool.DirectorToolDefinition;
import com.example.gameworkbench.director.tool.DirectorToolRegistry;
import com.example.gameworkbench.director.tool.ToolPermission;
import com.example.gameworkbench.director.tool.ToolRiskLevel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

class SpringAiDirectorDecisionClientTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void convertsOneSpringAiToolCallIntoPersistableDirectorDecision() {
        AtomicReference<Prompt> captured = new AtomicReference<>();
        ChatModel model = prompt -> {
            captured.set(prompt);
            var call = new AssistantMessage.ToolCall("provider-call-1", "function", "GET_GAMESPEC_CAPABILITIES", "{}");
            var message = AssistantMessage.builder().content("").toolCalls(List.of(call)).build();
            return ChatResponse.builder().generations(List.of(new Generation(message)))
                    .metadata(ChatResponseMetadata.builder().model("test-model")
                            .usage(new DefaultUsage(10, 4, 14)).build()).build();
        };
        DirectorToolRegistry registry = registry();
        var client = new SpringAiDirectorDecisionClient(ChatClient.builder(model), json,
                new SpringAiDirectorTools(registry, json), new ProjectContextAdvisor(),
                new CapabilityBoundaryAdvisor(), new ModelCallEvidenceAdvisor(), "configured-model");

        var decision = client.decide(snapshot(), "trace-1");

        assertThat(decision.path("kind").asText()).isEqualTo("CALL_TOOL");
        assertThat(decision.path("round").asInt()).isEqualTo(1);
        assertThat(decision.path("toolCall").path("toolName").asText()).isEqualTo("GET_GAMESPEC_CAPABILITIES");
        assertThat(decision.path("toolCall").path("callId").asText()).isEqualTo("run-1:1");
        assertThat(decision.path("modelEvidence").path("provider").asText()).isEqualTo("spring-ai-openai-compatible");
        assertThat(decision.path("modelEvidence").path("tokenUsage").asInt()).isEqualTo(14);
        assertThat(decision.path("modelEvidence").path("inputDigest").asText()).hasSize(64);
        assertThat(decision.path("decisionDigest").asText()).hasSize(64);
        assertThat(captured.get().getSystemMessage().getText())
                .contains("PROJECT CONTEXT", "AUTHORITATIVE CAPABILITY BOUNDARY");
    }

    @Test
    void mapsControlToolWithoutExecutingTheRegistry() {
        ChatModel model = prompt -> {
            var call = new AssistantMessage.ToolCall("finish-1", "function", SpringAiDirectorTools.FINISH,
                    "{\"summary\":\"target satisfied\",\"consumedToolResultDigests\":[]}");
            var message = AssistantMessage.builder().content("").toolCalls(List.of(call)).build();
            return new ChatResponse(List.of(new Generation(message)));
        };
        var client = new SpringAiDirectorDecisionClient(ChatClient.builder(model), json,
                new SpringAiDirectorTools(registry(), json), new ProjectContextAdvisor(),
                new CapabilityBoundaryAdvisor(), new ModelCallEvidenceAdvisor(), "test-model");

        var decision = client.decide(snapshot(), "trace-2");

        assertThat(decision.path("kind").asText()).isEqualTo("FINISH");
        assertThat(decision.path("outcome").path("summary").asText()).isEqualTo("target satisfied");
    }

    private DirectorToolRegistry registry() {
        DirectorToolRegistry registry = mock(DirectorToolRegistry.class);
        ObjectNode schema = json.createObjectNode().put("type", "object").put("additionalProperties", false);
        schema.putObject("properties");
        schema.putArray("required");
        when(registry.discover()).thenReturn(List.of(new DirectorToolDefinition("GET_GAMESPEC_CAPABILITIES", "1",
                schema, ToolPermission.READ, ToolRiskLevel.LOW, 5_000, 2_048, true)));
        return registry;
    }

    private ObjectNode snapshot() {
        ObjectNode snapshot = json.createObjectNode();
        snapshot.put("protocolVersion", "director/1.0");
        snapshot.put("runId", "run-1");
        snapshot.put("projectId", "7");
        snapshot.put("stateVersion", 2);
        snapshot.put("targetMet", true);
        snapshot.putObject("goal").put("sourceTextDigest", "a".repeat(64));
        snapshot.putObject("usage").put("rounds", 0);
        snapshot.putArray("recentToolResults");
        ObjectNode allowed = snapshot.putArray("allowedTools").addObject();
        allowed.put("name", "GET_GAMESPEC_CAPABILITIES").put("version", "1");
        return snapshot;
    }
}
