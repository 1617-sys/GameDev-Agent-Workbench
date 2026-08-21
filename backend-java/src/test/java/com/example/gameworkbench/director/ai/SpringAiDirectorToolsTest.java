package com.example.gameworkbench.director.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ToolContext;

import com.example.gameworkbench.director.tool.DirectorToolContext;
import com.example.gameworkbench.director.tool.DirectorToolDefinition;
import com.example.gameworkbench.director.tool.DirectorToolRegistry;
import com.example.gameworkbench.director.tool.ToolCallRequest;
import com.example.gameworkbench.director.tool.ToolCallResult;
import com.example.gameworkbench.director.tool.ToolPermission;
import com.example.gameworkbench.director.tool.ToolRiskLevel;
import com.fasterxml.jackson.databind.ObjectMapper;

class SpringAiDirectorToolsTest {
    @Test
    void callbackDelegatesOnlyWithTrustedServerContext() throws Exception {
        ObjectMapper json = new ObjectMapper();
        DirectorToolRegistry registry = mock(DirectorToolRegistry.class);
        var schema = json.createObjectNode().put("type", "object").put("additionalProperties", false);
        schema.putObject("properties");
        schema.putArray("required");
        var definition = new DirectorToolDefinition("GET_GAMESPEC_CAPABILITIES", "1", schema,
                ToolPermission.READ, ToolRiskLevel.LOW, 5_000, 2_048, true);
        when(registry.discover()).thenReturn(List.of(definition));
        when(registry.execute(any(), any())).thenReturn(new ToolCallResult("run:1", definition.name(), "1",
                "SUCCEEDED", "a".repeat(64), "b".repeat(64), "{}", "result://1", 2, null));
        var tools = new SpringAiDirectorTools(registry, json);
        var snapshot = json.createObjectNode();
        snapshot.putArray("allowedTools").addObject().put("name", definition.name()).put("version", "1");
        var callback = tools.callbacks(snapshot).stream()
                .filter(value -> value.getToolDefinition().name().equals(definition.name())).findFirst().orElseThrow();

        String output = callback.call("{}", new ToolContext(Map.of(
                SpringAiDirectorTools.USER_ID, 7L,
                SpringAiDirectorTools.PROJECT_ID, 9L,
                SpringAiDirectorTools.RUN_ID, "run",
                SpringAiDirectorTools.CALL_ID, "run:1",
                SpringAiDirectorTools.IDEMPOTENCY_KEY, "run:1")));

        ArgumentCaptor<DirectorToolContext> context = ArgumentCaptor.forClass(DirectorToolContext.class);
        ArgumentCaptor<ToolCallRequest> request = ArgumentCaptor.forClass(ToolCallRequest.class);
        verify(registry).execute(context.capture(), request.capture());
        assertThat(context.getValue()).isEqualTo(new DirectorToolContext(7, 9, "run", "run:1"));
        assertThat(request.getValue().idempotencyKey()).isEqualTo("run:1");
        assertThat(json.readTree(output).path("status").asText()).isEqualTo("SUCCEEDED");
    }
}
