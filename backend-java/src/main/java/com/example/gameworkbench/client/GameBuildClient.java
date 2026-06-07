package com.example.gameworkbench.client;

import com.example.gameworkbench.client.dto.GameBuildRequest;
import com.example.gameworkbench.client.dto.GameBuildResponse;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameBuildClient {

    @Value("${game-build.base-url:http://localhost:5173}")
    private String baseUrl;

    public GameBuildResponse invoke(GameBuildRequest request) {
        try {
            log.info("[GameBuild] mock build started projectUuid={} title={}",
                    request.getProjectUuid(), request.getTitle());

            return GameBuildResponse.builder()
                    .status("SUCCESS")
                    .title(request.getTitle())
                    .content(request.getContent())
                    .demoUrl(baseUrl + "/demo/mock-game")
                    .message("Mock game build completed")
                    .build();

        } catch (Exception exception) {
            log.error("[GameBuild] mock build failed projectUuid={}",
                    request.getProjectUuid(), exception);

            throw new BusinessException(ErrorCode.GAME_BUILD_FAILED);
        }
    }
}
