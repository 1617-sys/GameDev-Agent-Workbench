package com.example.gameworkbench.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

abstract class OpenApiSnapshotSupport {
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    protected void verifySnapshot(String profile) throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode springDoc = mapper.readTree(body);
        assertThat(springDoc.path("openapi").asText()).as("SpringDoc OpenAPI document version").isNotBlank();
        assertThat(springDoc.path("paths").isObject()).as("SpringDoc paths object").isTrue();
        assertThat(springDoc.path("paths").size()).as("SpringDoc paths must not be empty").isPositive();
        JsonNode actual = normalize(springDoc, profile);
        Path snapshot = locate("docs/api-coverage/openapi-" + profile + ".json");
        if (Boolean.getBoolean("openapi.update")) {
            Files.createDirectories(snapshot.getParent());
            Files.writeString(snapshot, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(actual) + "\n");
            return;
        }
        assertThat(snapshot).as("checked-in SpringDoc snapshot for " + profile).exists();
        JsonNode expected = mapper.readTree(snapshot.toFile());
        assertThat(actual).as("SpringDoc contract drift for profile " + profile).isEqualTo(expected);
    }

    private JsonNode normalize(JsonNode document, String profile) {
        ObjectNode root = mapper.createObjectNode();
        root.put("openapi", document.path("openapi").asText());
        ObjectNode info = mapper.createObjectNode();
        info.put("title", document.path("info").path("title").asText());
        info.put("version", document.path("info").path("version").asText());
        root.set("info", info);
        root.put("x-runtime-profile", profile);
        root.set("paths", sorted(document.path("paths")));
        root.set("components", sorted(document.path("components")));
        return root;
    }

    private JsonNode sorted(JsonNode node) {
        if (node.isObject()) {
            ObjectNode result = mapper.createObjectNode();
            List<String> names = new ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            names.stream().sorted(Comparator.naturalOrder()).forEach(name -> result.set(name, sorted(node.get(name))));
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = mapper.createArrayNode();
            node.forEach(value -> result.add(sorted(value)));
            return result;
        }
        return node.deepCopy();
    }

    private static Path locate(String workspaceRelativePath) {
        Path direct = Path.of(workspaceRelativePath);
        return Files.exists(direct.getParent()) ? direct : Path.of("..").resolve(workspaceRelativePath).normalize();
    }
}
