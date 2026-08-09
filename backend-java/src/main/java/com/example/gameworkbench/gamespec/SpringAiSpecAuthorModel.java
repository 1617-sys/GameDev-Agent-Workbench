package com.example.gameworkbench.gamespec;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.StructuredOutputValidationAdvisor;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.example.gameworkbench.ai.advisor.AiAdvisorContext;
import com.example.gameworkbench.ai.advisor.CapabilityBoundaryAdvisor;
import com.example.gameworkbench.ai.advisor.ModelCallEvidenceAdvisor;
import com.example.gameworkbench.ai.advisor.ProjectContextAdvisor;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import static com.example.gameworkbench.observability.DiagnosticContext.TRACE_ID;

@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "spring-ai", matchIfMissing = true)
public final class SpringAiSpecAuthorModel implements SpecAuthorModel {
    private static final String PROMPT_VERSION = "gamespec-author/2";
    private final ChatClient chat;
    private final ObjectMapper json;
    private final ArcadeCollectCapabilityRegistry capabilities;
    private final ProjectContextAdvisor projectContextAdvisor;
    private final CapabilityBoundaryAdvisor capabilityBoundaryAdvisor;
    private final ModelCallEvidenceAdvisor evidenceAdvisor;
    private final StructuredOutputValidationAdvisor validationAdvisor;
    private final BeanOutputConverter<GameSpecDraft> converter;
    private final String model;
    private final String example;

    public SpringAiSpecAuthorModel(ChatClient.Builder builder, ObjectMapper json,
            ArcadeCollectCapabilityRegistry capabilities, ProjectContextAdvisor projectContextAdvisor,
            CapabilityBoundaryAdvisor capabilityBoundaryAdvisor, ModelCallEvidenceAdvisor evidenceAdvisor,
            @Value("${app.ai.model:${LLM_MODEL:deepseek-chat}}") String model) {
        this.chat = builder.build();
        this.json = json;
        this.capabilities = capabilities;
        this.projectContextAdvisor = projectContextAdvisor;
        this.capabilityBoundaryAdvisor = capabilityBoundaryAdvisor;
        this.evidenceAdvisor = evidenceAdvisor;
        this.validationAdvisor = StructuredOutputValidationAdvisor.builder().outputType(GameSpecDraft.class)
                .objectMapper(json).maxRepeatAttempts(2).build();
        this.converter = new BeanOutputConverter<>(GameSpecDraft.class, json);
        this.model = model;
        try (var input = new ClassPathResource("gamespec/arcade-collect-valid.json").getInputStream()) {
            this.example = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("GameSpec author fixture is missing", exception);
        }
    }

    @Override
    public SpecAuthorModelResponse author(SpecAuthorModelRequest request) {
        String prompt = """
                Create one GameSpec for the constrained arcade_collect game described below.
                The response must satisfy the supplied JSON schema. Do not add wrappers or explanations.
                Java compiler diagnostics are authoritative; fix every ERROR item before making other changes.

                IDEA:
                %s

                CURRENT SPEC (repair it when present):
                %s

                COMPILER DIAGNOSTICS:
                %s

                VALID SEMANTIC EXAMPLE:
                %s
                """.formatted(request.idea(), request.currentSpec() == null ? "none" : request.currentSpec(),
                        request.diagnostics(), example);
        try {
            ChatClientResponse advised = chat.prompt()
                    .system("You are the GameSpec Author. Produce only capabilities supported by the Java compiler.")
                    .user(prompt)
                    .options(OpenAiChatOptions.builder().model(model).temperature(0.2).build())
                    .advisors(projectContextAdvisor, capabilityBoundaryAdvisor, validationAdvisor, evidenceAdvisor)
                    .advisors(spec -> spec.params(Map.of(
                            AiAdvisorContext.PROJECT_CONTEXT, projectContext(request),
                            AiAdvisorContext.CAPABILITY_BOUNDARY, write(capabilities.snapshot()),
                            AiAdvisorContext.OPERATION, "gamespec-author",
                            AiAdvisorContext.PROMPT_VERSION, PROMPT_VERSION,
                            AiAdvisorContext.TRACE_ID, traceId(),
                            AiAdvisorContext.PROJECT_ID, request.projectUuid(),
                            AiAdvisorContext.RUN_ID, request.projectUuid() + ":author:" + request.attempt())))
                    .call().chatClientResponse();
            if (advised.chatResponse() == null || advised.chatResponse().getResult() == null) invalid();
            GameSpecDraft draft = converter.convert(advised.chatResponse().getResult().getOutput().getText());
            if (draft == null) invalid();
            ObjectNode spec = (ObjectNode) pruneNulls(json.valueToTree(draft));
            return new SpecAuthorModelResponse(spec, evidence(advised));
        } catch (BusinessException exception) { throw exception; }
        catch (Exception exception) { throw new BusinessException(ErrorCode.AI_MODEL_INVALID_RESPONSE); }
    }

    private String projectContext(SpecAuthorModelRequest request) {
        ObjectNode context = json.createObjectNode();
        context.put("projectUuid", request.projectUuid());
        context.put("repairAttempt", request.attempt());
        if (request.currentSpec() != null) context.set("currentSpec", request.currentSpec());
        context.put("compilerDiagnostics", request.diagnostics());
        return write(context);
    }

    private ObjectNode evidence(ChatClientResponse response) {
        ObjectNode evidence = json.createObjectNode();
        Object raw = response.context().get(AiAdvisorContext.EVIDENCE);
        if (raw != null && json.valueToTree(raw).isObject()) evidence.setAll((ObjectNode) json.valueToTree(raw));
        evidence.put("provider", "spring-ai-openai-compatible");
        var metadata = response.chatResponse().getMetadata();
        evidence.put("model", metadata != null && metadata.getModel() != null ? metadata.getModel() : model);
        evidence.put("promptVersion", PROMPT_VERSION);
        var usage = metadata == null ? null : metadata.getUsage();
        evidence.put("tokenUsage", usage == null || usage.getTotalTokens() == null ? 0 : usage.getTotalTokens());
        evidence.put("costMicros", 0);
        return evidence;
    }

    private JsonNode pruneNulls(JsonNode value) {
        if (value.isObject()) {
            ObjectNode object = (ObjectNode) value;
            java.util.List<String> remove = new java.util.ArrayList<>();
            object.fields().forEachRemaining(entry -> {
                if (entry.getValue().isNull()) remove.add(entry.getKey());
                else pruneNulls(entry.getValue());
            });
            object.remove(remove);
        } else if (value.isArray()) {
            ArrayNode array = (ArrayNode) value;
            array.forEach(this::pruneNulls);
        }
        return value;
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private String traceId() {
        String value = MDC.get(TRACE_ID);
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value;
    }

    private void invalid() { throw new BusinessException(ErrorCode.AI_MODEL_INVALID_RESPONSE); }
}
