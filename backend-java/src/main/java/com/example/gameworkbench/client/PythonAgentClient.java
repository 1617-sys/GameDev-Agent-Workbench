package com.example.gameworkbench.client;

import com.example.gameworkbench.client.dto.PythonAgentRequest;
import com.example.gameworkbench.client.dto.PythonAgentResponse;
import com.example.gameworkbench.common.enums.AgentType;
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
            log.warn("[Python] 调用失败：Agent 类型不能为空");
            throw new BusinessException(40001, "Agent类型不能为空");
        }
        if (!StringUtils.hasText(baseUrl)) {
            log.error("[Python] 调用失败：Python 服务地址未配置 agentType={}", agentType);
            throw new BusinessException(50002, "Python服务地址未配置");
        }

        String url = baseUrl + agentType.getPythonPath();
        String responseBody;
        long startTime = System.currentTimeMillis();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<PythonAgentRequest> entity = new HttpEntity<>(request, headers);
            log.info("[Python] 调用开始 agentType={} url={}", agentType, url);
            responseBody = restTemplate.postForObject(url, entity, String.class);
            log.info("[Python] 调用完成 agentType={} url={} timeTakenMs={}",
                    agentType, url, System.currentTimeMillis() - startTime);
        } catch (Exception exception) {
            log.error("[Python] 调用异常 agentType={} url={} timeTakenMs={}",
                    agentType, url, System.currentTimeMillis() - startTime, exception);
            throw new BusinessException(50201, "调用Python服务失败");
        }

        if (!StringUtils.hasText(responseBody)) {
            log.warn("[Python] 调用失败：返回内容为空 agentType={} url={}", agentType, url);
            throw new BusinessException(50202, "Python服务未返回结果");
        }

        try {
            PythonAgentResponse response = objectMapper.readValue(responseBody, PythonAgentResponse.class);
            if (response.getCode() == null) {
                log.warn("[Python] 调用失败：返回 code 为空 agentType={} url={}", agentType, url);
                throw new BusinessException(50202, "Python服务返回格式不正确");
            }
            if (!Objects.equals(response.getCode(), 0)) {
                String message = StringUtils.hasText(response.getMessage())
                        ? response.getMessage()
                        : "Python服务返回失败";
                log.warn("[Python] 调用返回失败 agentType={} url={} code={} message={}",
                        agentType, url, response.getCode(), message);
                throw new BusinessException(50203, message);
            }
            log.info("[Python] 调用成功 agentType={} url={} code={}", agentType, url, response.getCode());
            return response;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("[Python] 解析返回结果异常 agentType={} url={}", agentType, url, exception);
            throw new BusinessException(50202, "解析Python返回结果失败");
        }
    }
}
