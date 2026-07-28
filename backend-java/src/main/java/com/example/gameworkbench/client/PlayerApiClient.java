package com.example.gameworkbench.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class PlayerApiClient {
    private final RestTemplate http;
    private final String baseUrl;
    private final String token;

    public PlayerApiClient(
            @Value("${app.python.base-url:http://127.0.0.1:8000}") String baseUrl,
            @Value("${app.python.internal-token}") String token,
            @Value("${app.player.http-timeout-ms:120000}") long timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout((int)Math.min(timeoutMs, Integer.MAX_VALUE));
        this.http = new RestTemplate(factory);
        this.baseUrl = baseUrl;
        this.token = token;
    }

    public JsonNode runBatch(JsonNode request, String traceId) {
        if (token == null || token.length() < 32) throw new BusinessException(ErrorCode.PYTHON_CALL_FAILED);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Token", token);
        headers.set("X-Trace-Id", traceId);
        try {
            log.info("[PlayerApi] batch started traceId={} items={}", traceId, request.path("episodes").size());
            JsonNode response = http.postForObject(baseUrl + "/player/episodes/batch", new HttpEntity<>(request, headers), JsonNode.class);
            if (response == null || !response.path("results").isArray()) throw new BusinessException(ErrorCode.PYTHON_INVALID_RESPONSE);
            return response;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("[PlayerApi] batch failed traceId={} exceptionType={}", traceId, exception.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.PYTHON_CALL_FAILED);
        }
    }
}
