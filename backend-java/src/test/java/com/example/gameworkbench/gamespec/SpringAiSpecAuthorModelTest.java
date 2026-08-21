package com.example.gameworkbench.gamespec;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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
import com.fasterxml.jackson.databind.ObjectMapper;

class SpringAiSpecAuthorModelTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void validatesStructuredOutputAndReturnsAdvisorEvidence() throws Exception {
        String valid = validSpec();
        AtomicReference<Prompt> captured = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        ChatModel model = prompt -> {
            captured.set(prompt);
            calls.incrementAndGet();
            var response = new AssistantMessage(valid);
            return ChatResponse.builder().generations(List.of(new Generation(response)))
                    .metadata(ChatResponseMetadata.builder().model("test-model")
                            .usage(new DefaultUsage(20, 30, 50)).build()).build();
        };
        ArcadeCollectCapabilityRegistry capabilities = new ArcadeCollectCapabilityRegistry(json);
        var author = new SpringAiSpecAuthorModel(ChatClient.builder(model), json, capabilities,
                new ProjectContextAdvisor(), new CapabilityBoundaryAdvisor(), new ModelCallEvidenceAdvisor(), "test-model");

        SpecAuthorModelResponse result = author.author(new SpecAuthorModelRequest(
                7, "project-1", "collect crystals", null, "none", 1));

        assertThat(calls).hasValue(1);
        assertThat(result.spec().path("archetype").asText()).isEqualTo("arcade_collect");
        assertThat(result.spec().path("entities").get(0).has("speed")).isFalse();
        assertThat(result.modelEvidence().path("tokenUsage").asInt()).isEqualTo(50);
        assertThat(result.modelEvidence().path("inputDigest").asText()).hasSize(64);
        assertThat(captured.get().getSystemMessage().getText())
                .contains("PROJECT CONTEXT", "AUTHORITATIVE CAPABILITY BOUNDARY");
    }

    @Test
    void structuredOutputAdvisorRepromptsAfterSchemaViolation() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        String valid = validSpec();
        ChatModel model = prompt -> {
            String content = calls.incrementAndGet() == 1 ? "{\"specVersion\":17}" : valid;
            return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
        };
        var author = new SpringAiSpecAuthorModel(ChatClient.builder(model), json,
                new ArcadeCollectCapabilityRegistry(json), new ProjectContextAdvisor(),
                new CapabilityBoundaryAdvisor(), new ModelCallEvidenceAdvisor(), "test-model");

        SpecAuthorModelResponse result = author.author(new SpecAuthorModelRequest(
                7, "project-1", "collect crystals", null, "none", 1));

        assertThat(calls).hasValue(2);
        assertThat(result.spec().path("specVersion").asText()).isEqualTo("0.1");
    }

    private String validSpec() throws Exception {
        return new String(getClass().getResourceAsStream("/gamespec/arcade-collect-valid.json").readAllBytes(),
                StandardCharsets.UTF_8);
    }
}
