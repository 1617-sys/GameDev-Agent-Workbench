package com.example.gameworkbench.director.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import org.junit.jupiter.api.Test;
import com.example.gameworkbench.gamespec.ArcadeCollectCapabilityRegistry;
import com.example.gameworkbench.gamespec.GameSpecCompiler;
import com.fasterxml.jackson.databind.ObjectMapper;

class GameSpecDirectorToolsTest {
    @Test
    void exposesOnlyCapabilityDiscoveryAndBoundedCompilation() throws Exception {
        ObjectMapper json = new ObjectMapper();
        var capabilities = new ArcadeCollectCapabilityRegistry(json);
        var tools = GameSpecDirectorTools.create(capabilities, new GameSpecCompiler(json, capabilities), json);

        assertThat(tools).extracting(tool -> tool.definition().name())
                .containsExactly("GET_GAMESPEC_CAPABILITIES", "COMPILE_GAME_SPEC");
        assertThat(tools).allSatisfy(tool -> assertThat(tool.definition().argumentSchema()
                .path("additionalProperties").asBoolean()).isFalse());

        try (InputStream stream = getClass().getResourceAsStream("/gamespec/arcade-collect-valid.json")) {
            var arguments = json.createObjectNode().put("specJson", json.writeValueAsString(json.readTree(stream)));
            var output = tools.get(1).execute(new DirectorToolContext(1, 2, "run", "call"), arguments);
            assertThat(output.path("status").asText()).isEqualTo("SUCCEEDED");
            assertThat(output.path("buildRequest").path("target").asText()).isEqualTo("web-mobile");
        }
    }
}
