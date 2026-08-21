package com.example.gameworkbench.cocos;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.gamespec.ArcadeCollectCapabilityRegistry;
import com.example.gameworkbench.gamespec.GameSpecCompiler;
import com.fasterxml.jackson.databind.ObjectMapper;

class CocosBuildWorkerTest {
    @TempDir
    Path tempDirectory;

    @Test
    void failsClosedWhenCocosIsNotConfigured() throws Exception {
        ObjectMapper json = new ObjectMapper();
        GameSpecCompiler compiler = new GameSpecCompiler(json, new ArcadeCollectCapabilityRegistry(json));
        try (InputStream stream = getClass().getResourceAsStream("/gamespec/arcade-collect-valid.json")) {
            var compiled = compiler.compile(json.readTree(stream));
            CocosBuildWorker worker = new CocosBuildWorker(json, "", "", System.getProperty("java.io.tmpdir"), "36");

            assertThatThrownBy(() -> worker.build(compiled.buildRequest(), compiled.runtimeIr()))
                    .isInstanceOfSatisfying(BusinessException.class,
                            error -> org.assertj.core.api.Assertions.assertThat(error.getCode())
                                    .isEqualTo(ErrorCode.COCOS_BUILD_UNAVAILABLE.getCode()));
        }
    }

    @Test
    void copiesReviewedRuntimeSourcesWithoutCreatorGeneratedState() throws Exception {
        Path source = tempDirectory.resolve("runtime");
        Path target = tempDirectory.resolve("isolated");
        Files.createDirectories(source.resolve("assets"));
        Files.writeString(source.resolve("assets/main.scene"), "scene");
        for (String generated : new String[] { ".git", "build", "library", "local", "native", "node_modules", "profiles", "temp" }) {
            Files.createDirectories(source.resolve(generated));
            Files.writeString(source.resolve(generated).resolve("machine-state"), "excluded");
        }
        CocosBuildWorker worker = new CocosBuildWorker(new ObjectMapper(), "", "", tempDirectory.toString(), "36");

        worker.copyTree(source, target);

        assertThat(target.resolve("assets/main.scene")).hasContent("scene");
        for (String generated : new String[] { ".git", "build", "library", "local", "native", "node_modules", "profiles", "temp" }) {
            assertThat(target.resolve(generated)).doesNotExist();
        }
    }
}
