package com.example.gameworkbench.gamespec;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class GameSpecCompilerTest {
    private ObjectMapper json;
    private GameSpecCompiler compiler;

    @BeforeEach
    void setUp() {
        json = new ObjectMapper();
        compiler = new GameSpecCompiler(json, new ArcadeCollectCapabilityRegistry(json));
    }

    @Test
    void compilesValidSpecIntoDeterministicCocosRequest() throws Exception {
        JsonNode spec = fixture();

        GameSpecCompilationResult first = compiler.compile(spec);
        GameSpecCompilationResult second = compiler.compile(spec.deepCopy());

        assertThat(first.status()).isEqualTo(GameSpecCompilationResult.Status.SUCCEEDED);
        assertThat(first.diagnostics()).isEmpty();
        assertThat(first.sourceDigest()).hasSize(64).isEqualTo(second.sourceDigest());
        assertThat(first.runtimeIrDigest()).hasSize(64).isEqualTo(second.runtimeIrDigest());
        assertThat(first.runtimeIr()).isEqualTo(second.runtimeIr());
        assertThat(first.buildRequest()).isEqualTo(second.buildRequest());
        assertThat(first.buildRequest().path("target").asText()).isEqualTo("web-mobile");
        assertThat(first.buildRequest().path("cocosCreatorVersion").asText()).isEqualTo("3.8.8");
        assertThat(first.buildRequest().path("gameSpecDigest").asText()).isEqualTo(first.sourceDigest());
    }

    @Test
    void rejectsUnknownExecutableCapabilityWithStableJsonPointer() throws Exception {
        JsonNode spec = fixture();
        ((com.fasterxml.jackson.databind.node.ObjectNode) spec.path("entities").get(0)).put("script", "eval('x')");

        GameSpecCompilationResult result = compiler.compile(spec);

        assertThat(result.status()).isEqualTo(GameSpecCompilationResult.Status.FAILED);
        assertThat(result.canonicalSpec()).isNull();
        assertThat(result.diagnostics()).anySatisfy(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("GS1001_UNKNOWN_FIELD");
            assertThat(diagnostic.path()).isEqualTo("/entities/0/script");
        });
    }

    @Test
    void rejectsUnregisteredPresentationProfile() throws Exception {
        JsonNode spec = fixture();
        ((com.fasterxml.jackson.databind.node.ObjectNode) spec.path("presentation"))
                .put("assetPackId", "remote-random-pack");

        GameSpecCompilationResult result = compiler.compile(spec);

        assertThat(result.diagnostics()).anySatisfy(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("GS1401_UNSUPPORTED_CAPABILITY");
            assertThat(diagnostic.path()).isEqualTo("/presentation/assetPackId");
            assertThat(diagnostic.allowedValues()).containsExactly("forest-adventure-01");
        });
    }

    @Test
    void rejectsWorldWithoutOneReachableExit() throws Exception {
        JsonNode spec = fixture();
        ((com.fasterxml.jackson.databind.node.ArrayNode) spec.path("entities")).remove(4);

        GameSpecCompilationResult result = compiler.compile(spec);

        assertThat(result.diagnostics()).anyMatch(diagnostic ->
                diagnostic.code().equals("GS1601_UNREACHABLE_WIN_CONDITION") && diagnostic.path().equals("/entities"));
    }

    private JsonNode fixture() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/gamespec/arcade-collect-valid.json")) {
            return json.readTree(stream);
        }
    }
}
