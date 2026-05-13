package com.example.gameworkbench.client;

import com.example.gameworkbench.client.dto.PythonAgentRequest;
import com.example.gameworkbench.client.dto.PythonAgentResponse;
import com.example.gameworkbench.common.enums.AgentType;
import com.example.gameworkbench.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class PythonAgentClient {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.python.base-url:http://127.0.0.1:8000}")
    private String baseUrl;

    public PythonAgentResponse invoke(AgentType agentType, PythonAgentRequest request) {
        if (agentType == null) {
            throw new BusinessException(40001, "Agent类型不能为空");
        }
        if (!StringUtils.hasText(baseUrl)) {
            throw new BusinessException(50002, "Python服务地址未配置");
        }

        String responseBody;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<PythonAgentRequest> entity = new HttpEntity<>(request, headers);
            responseBody = restTemplate.postForObject(baseUrl + agentType.getPythonPath(), entity, String.class);
        } catch (Exception exception) {
            throw new BusinessException(50201, "调用Python服务失败");
        }

        if (!StringUtils.hasText(responseBody)) {
            throw new BusinessException(50202, "Python服务未返回结果");
        }

        try {
            PythonAgentResponse response = objectMapper.readValue(responseBody, PythonAgentResponse.class);
            if (response.getCode() == null) {
                throw new BusinessException(50202, "Python服务返回格式不正确");
            }
            if (!Objects.equals(response.getCode(), 0)) {
                String message = StringUtils.hasText(response.getMessage())
                        ? response.getMessage()
                        : "Python服务返回失败";
                throw new BusinessException(50203, message);
            }
            return response;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(50202, "解析Python返回结果失败");
        }
    }
}
