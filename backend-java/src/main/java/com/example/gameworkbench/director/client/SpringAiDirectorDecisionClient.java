package com.example.gameworkbench.director.client;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import com.example.gameworkbench.ai.advisor.AiAdvisorContext;
import com.example.gameworkbench.ai.advisor.CapabilityBoundaryAdvisor;
import com.example.gameworkbench.ai.advisor.ModelCallEvidenceAdvisor;
import com.example.gameworkbench.ai.advisor.ProjectContextAdvisor;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.director.ai.SpringAiDirectorTools;
import com.example.gameworkbench.director.tool.DirectorToolDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Component
@ConditionalOnProperty(name = "app.director.decision-provider", havingValue = "spring-ai", matchIfMissing = true)
@ConditionalOnBean(ChatModel.class)
public final class SpringAiDirectorDecisionClient implements DirectorDecisionClient {
    private static final String PROMPT_VERSION = "spring-ai-director/1";
    private static final String SYSTEM = """
            You are a bounded game-design Director operating inside a durable Java workflow.
            Select exactly one supplied tool per turn. Never answer with plain text.
            Domain tools request one allowlisted operation. DIRECTOR_FINISH, DIRECTOR_REQUEST_APPROVAL,
            and DIRECTOR_FAIL are terminal control decisions interpreted by Java.
            Never invent tool results, resource identifiers, approvals, or capabilities.
            Prefer inspecting current state before mutating it. Request approval only after a draft exists.
            """;

    private final ChatClient chat;
    private final ObjectMapper json;
    private final SpringAiDirectorTools tools;
    private final ProjectContextAdvisor projectContextAdvisor;
    private final CapabilityBoundaryAdvisor capabilityBoundaryAdvisor;
    private final ModelCallEvidenceAdvisor evidenceAdvisor;
    private final String model;

    public SpringAiDirectorDecisionClient(ChatClient.Builder builder, ObjectMapper json, SpringAiDirectorTools tools,
            ProjectContextAdvisor projectContextAdvisor, CapabilityBoundaryAdvisor capabilityBoundaryAdvisor,
            ModelCallEvidenceAdvisor evidenceAdvisor,
            @Value("${app.director.model:${app.ai.model:${LLM_MODEL:deepseek-chat}}}") String model) {
        this.chat = builder.build();
        this.json = json;
        this.tools = tools;
        this.projectContextAdvisor = projectContextAdvisor;
        this.capabilityBoundaryAdvisor = capabilityBoundaryAdvisor;
        this.evidenceAdvisor = evidenceAdvisor;
        this.model = model;
    }

    @Override
    public JsonNode decide(JsonNode snapshot, String traceId) {
        try {
            int round = snapshot.path("usage").path("rounds").asInt() + 1;
            OpenAiChatOptions options = OpenAiChatOptions.builder().model(model).temperature(0.1)
                    .parallelToolCalls(false).internalToolExecutionEnabled(false).build();
            ChatClientResponse advised = chat.prompt().system(SYSTEM)
                    .user("Choose the single safest next action for this persisted Director snapshot:\n" + write(snapshot))
                    .options(options).toolCallbacks(tools.callbacks(snapshot))
                    .advisors(projectContextAdvisor, capabilityBoundaryAdvisor, evidenceAdvisor)
                    .advisors(spec -> spec.params(Map.of(
                            AiAdvisorContext.PROJECT_CONTEXT, projectContext(snapshot),
                            AiAdvisorContext.CAPABILITY_BOUNDARY, write(snapshot.path("allowedTools")),
                            AiAdvisorContext.OPERATION, "director-decision",
                            AiAdvisorContext.PROMPT_VERSION, PROMPT_VERSION,
                            AiAdvisorContext.TRACE_ID, traceId,
                            AiAdvisorContext.PROJECT_ID, snapshot.path("projectId").asText(),
                            AiAdvisorContext.RUN_ID, snapshot.path("runId").asText())))
                    .call().chatClientResponse();
            ChatResponse response = advised.chatResponse();
            if (response == null || response.getResult() == null) invalid();
            List<AssistantMessage.ToolCall> calls = response.getResult().getOutput().getToolCalls();
            if (calls == null || calls.size() != 1) invalid();
            AssistantMessage.ToolCall call = calls.getFirst();
            ObjectNode arguments = object(call.arguments());
            tools.validate(call.name(), arguments);
            ObjectNode decision = base(snapshot, round, call.name(), response.getResult().getOutput().getText());
            if (SpringAiDirectorTools.FINISH.equals(call.name())) {
                if (!snapshot.path("targetMet").asBoolean(false)) invalid();
                decision.put("kind", "FINISH").set("outcome", arguments);
            } else if (SpringAiDirectorTools.REQUEST_APPROVAL.equals(call.name())) {
                if (!snapshot.path("targetMet").asBoolean(false)
                        || !snapshot.path("approvalRequired").asBoolean(false)) invalid();
                decision.put("kind", "REQUEST_APPROVAL").set("approval", arguments);
            } else if (SpringAiDirectorTools.FAIL.equals(call.name())) {
                decision.put("kind", "FAIL").set("error", arguments);
            } else {
                DirectorToolDefinition definition = tools.registered(call.name());
                if (definition == null || !allowed(snapshot, definition)) invalid();
                ObjectNode toolCall = decision.put("kind", "CALL_TOOL").putObject("toolCall");
                String callId = snapshot.path("runId").asText() + ":" + round;
                toolCall.put("callId", bounded(callId, 80));
                toolCall.put("toolName", definition.name());
                toolCall.put("toolVersion", definition.version());
                toolCall.put("idempotencyKey", bounded(callId, 128));
                toolCall.set("arguments", arguments);
                toolCall.put("dryRun", false);
            }
            decision.set("modelEvidence", evidence(advised, response));
            decision.put("decisionDigest", digest(write(decision)));
            return decision;
        } catch (BusinessException exception) { throw exception; }
        catch (Exception exception) { throw new BusinessException(ErrorCode.AI_MODEL_INVALID_RESPONSE); }
    }

    private ObjectNode base(JsonNode snapshot, int round, String toolName, String explanation) {
        ObjectNode decision = json.createObjectNode();
        decision.put("protocolVersion", "director/1.0");
        decision.put("runId", snapshot.path("runId").asText());
        decision.put("stateVersion", snapshot.path("stateVersion").asLong());
        decision.put("round", round);
        String reason = explanation == null || explanation.isBlank() ? "Spring AI selected " + toolName : explanation;
        decision.put("reasonSummary", bounded(reason, 500));
        return decision;
    }

    private ObjectNode evidence(ChatClientResponse advised, ChatResponse response) {
        ObjectNode evidence = json.createObjectNode();
        Object raw = advised.context().get(AiAdvisorContext.EVIDENCE);
        if (raw != null) evidence.setAll((ObjectNode) json.valueToTree(raw));
        evidence.put("provider", "spring-ai-openai-compatible");
        evidence.put("model", response.getMetadata() != null && response.getMetadata().getModel() != null
                ? response.getMetadata().getModel() : model);
        evidence.put("promptVersion", PROMPT_VERSION);
        var usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        evidence.put("tokenUsage", usage == null || usage.getTotalTokens() == null ? 0 : usage.getTotalTokens());
        evidence.put("costMicros", 0);
        return evidence;
    }

    private boolean allowed(JsonNode snapshot, DirectorToolDefinition definition) {
        for (JsonNode allowed : snapshot.path("allowedTools")) {
            if (definition.name().equals(allowed.path("name").asText())
                    && definition.version().equals(allowed.path("version").asText())) return true;
        }
        return false;
    }

    private String projectContext(JsonNode snapshot) {
        ObjectNode context = json.createObjectNode();
        context.set("goal", snapshot.path("goal"));
        context.set("usage", snapshot.path("usage"));
        context.set("recentToolResults", snapshot.path("recentToolResults"));
        if (snapshot.has("targetMet")) context.set("targetMet", snapshot.path("targetMet"));
        if (snapshot.has("approvalRequired")) context.set("approvalRequired", snapshot.path("approvalRequired"));
        return write(context);
    }

    private ObjectNode object(String value) {
        try {
            JsonNode parsed = json.readTree(value);
            if (parsed == null || !parsed.isObject()) invalid();
            return (ObjectNode) parsed;
        } catch (BusinessException exception) { throw exception; }
        catch (Exception exception) { invalid(); return null; }
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private String bounded(String value, int max) { return value.length() <= max ? value : value.substring(0, max); }
    private void invalid() { throw new BusinessException(ErrorCode.AI_MODEL_INVALID_RESPONSE); }
}
