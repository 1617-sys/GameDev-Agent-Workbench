package com.example.gameworkbench.client;

import com.example.gameworkbench.client.dto.PythonAgentRequest;
import com.example.gameworkbench.client.dto.PythonAgentResponse;
import com.example.gameworkbench.common.enums.AgentType;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class PythonAgentClient {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.python.base-url:http://127.0.0.1:8000}")
    private String baseUrl;

    public PythonAgentResponse invoke(AgentType agentType, PythonAgentRequest request) {
        if (agentType == null) {
            log.warn("[Python] call rejected: agentType is null");
            throw new BusinessException(ErrorCode.AGENT_TYPE_REQUIRED);
        }
        if (!StringUtils.hasText(baseUrl)) {
            log.error("[Python] call rejected: base URL is not configured agentType={}", agentType);
            throw new BusinessException(ErrorCode.PYTHON_BASE_URL_NOT_CONFIGURED);
        }

        String url = baseUrl + agentType.getPythonPath();
        String responseBody;
        long startTime = System.currentTimeMillis();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<PythonAgentRequest> entity = new HttpEntity<>(request, headers);
            log.info("[Python] call started agentType={} url={}", agentType, url);
            responseBody = restTemplate.postForObject(url, entity, String.class);
            log.info("[Python] call finished agentType={} url={} timeTakenMs={}",
                    agentType, url, System.currentTimeMillis() - startTime);
        } catch (Exception exception) {
            log.error("[Python] call exception agentType={} url={} timeTakenMs={}",
                    agentType, url, System.currentTimeMillis() - startTime, exception);
            throw new BusinessException(ErrorCode.PYTHON_CALL_FAILED);
        }

        if (!StringUtils.hasText(responseBody)) {
            log.warn("[Python] call failed: empty response agentType={} url={}", agentType, url);
            throw new BusinessException(ErrorCode.PYTHON_EMPTY_RESPONSE);
        }

        try {
            PythonAgentResponse response = objectMapper.readValue(responseBody, PythonAgentResponse.class);
            if (response.getCode() == null) {
                log.warn("[Python] call failed: response code is null agentType={} url={}", agentType, url);
                throw new BusinessException(ErrorCode.PYTHON_INVALID_RESPONSE);
            }
            if (!Objects.equals(response.getCode(), 0)) {
                String message = StringUtils.hasText(response.getMessage())
                        ? response.getMessage()
                        : ErrorCode.PYTHON_RESPONSE_FAILED.getMessage();
                log.warn("[Python] call returned failure agentType={} url={} code={} message={}",
                        agentType, url, response.getCode(), message);
                throw new BusinessException(ErrorCode.PYTHON_RESPONSE_FAILED.getCode(), message);
            }
            log.info("[Python] call succeeded agentType={} url={} code={}", agentType, url, response.getCode());
            return response;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("[Python] response parse exception agentType={} url={}", agentType, url, exception);
            throw new BusinessException(ErrorCode.PYTHON_RESPONSE_PARSE_FAILED);
        }
    }
}
