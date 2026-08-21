package com.example.gameworkbench.artifact;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.example.gameworkbench.entity.GenerationRun;
import com.example.gameworkbench.gamespec.ArcadeCollectCapabilityRegistry;
import com.example.gameworkbench.gamespec.GameSpecCompiler;
import com.fasterxml.jackson.databind.ObjectMapper;

class PlayableArtifactAssemblerTest {
    @TempDir Path temporary;

    @Test
    void assemblesDeterministicStandalonePackageWithProvenance() throws Exception {
        ObjectMapper json = new ObjectMapper();
        var capabilities = new ArcadeCollectCapabilityRegistry(json);
        var compiler = new GameSpecCompiler(json, capabilities);
        var stream = getClass().getResourceAsStream("/gamespec/arcade-collect-valid.json");
        var compiled = compiler.compile(json.readTree(stream));
        Path game = temporary.resolve("build");
        Files.createDirectories(game.resolve("assets"));
        Files.writeString(game.resolve("index.html"), "<!doctype html><script src=\"assets/main.js\"></script>");
        Files.writeString(game.resolve("assets/main.js"), "console.log('ready')");
        GenerationRun run = GenerationRun.builder().runUuid("123e4567-e89b-12d3-a456-426614174000")
                .projectId(7L).status("BUILDING").sourceDigest(compiled.sourceDigest())
                .runtimeIrDigest(compiled.runtimeIrDigest()).canonicalSpecJson(json.writeValueAsString(compiled.canonicalSpec()))
                .runtimeIrJson(json.writeValueAsString(compiled.runtimeIr()))
                .buildRequestJson(json.writeValueAsString(compiled.buildRequest())).build();
        PlayableArtifactAssembler assembler = new PlayableArtifactAssembler(json);
        String logDigest = "a".repeat(64);

        PlayableArtifact first = assembler.assemble(run, game, logDigest);
        PlayableArtifact second = assembler.assemble(run, game, logDigest);

        assertThat(first.packageDigest()).hasSize(64).isEqualTo(second.packageDigest());
        assertThat(first.payloadDigest()).hasSize(64);
        assertThat(first.manifest().path("artifactType").asText()).isEqualTo("LOCAL_COCOS_WEB_PACKAGE");
        assertThat(first.manifest().path("sourceDigest").asText()).isEqualTo(compiled.sourceDigest());
        assertThat(entries(first.zipBytes())).contains("artifact-manifest.json", "game/index.html", "game/assets/main.js",
                "provenance/game-spec.json", "provenance/runtime-ir.json", "provenance/build-request.json",
                "evidence/build-record.json", "launch.ps1", "README.md");
    }

    private Set<String> entries(byte[] zip) throws Exception {
        Set<String> names = new HashSet<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) names.add(entry.getName());
        }
        return names;
    }
}
