package com.example.gameworkbench.client;

import com.example.gameworkbench.client.dto.GameBuildRequest;
import com.example.gameworkbench.client.dto.GameBuildResponse;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameBuildClient {

    @Value("${game-build.base-url:http://localhost:5173}")
    private String baseUrl;

    public GameBuildResponse invoke(GameBuildRequest request) {
        long startTime = System.currentTimeMillis();
        try {
            log.info("[GameBuild] mock build started projectUuid={} title={}",
                    request.getProjectUuid(), request.getTitle());

            String demoUrl = baseUrl
                    + "/demo/play"
                    + "?projectUuid=" + encode(request.getProjectUuid())
                    + "&title=" + encode(request.getTitle())
                    + "&artifactUuid=" + encode(request.getGameConfigArtifactUuid());
            long timeTakenMs = System.currentTimeMillis() - startTime;

            log.info("[GameBuild] mock build succeeded projectUuid={} demoUrl={} timeTakenMs={}",
                    request.getProjectUuid(), demoUrl, timeTakenMs);

            return GameBuildResponse.builder()
                    .status("SUCCESS")
                    .title(request.getTitle())
                    .content(request.getContent())
                    .demoUrl(demoUrl)
                    .buildId(UUID.randomUUID().toString())
                    .timeTakenMs(timeTakenMs)
                    .message("Mock game build completed")
                    .build();

        } catch (Exception exception) {
            log.error("[GameBuild] mock build failed projectUuid={}",
                    request.getProjectUuid(), exception);

            throw new BusinessException(ErrorCode.GAME_BUILD_FAILED);
        }
    }

    private String encode(String value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
