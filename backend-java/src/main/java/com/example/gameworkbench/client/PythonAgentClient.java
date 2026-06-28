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

    /**
     * 调用远程 Python Agent 服务并返回响应结果。
     *
     * <p>处理流程：校验入参和配置 → 通过 RestTemplate 发送 POST 请求到 Python 服务
     * → 校验响应体非空 → 解析 JSON 并校验业务状态码 → 返回响应。</p>
     *
     * @param agentType Agent 类型，决定调用 Python 服务的具体路径
     * @param request   发往 Python Agent 的请求体
     * @return Python Agent 的解析后响应对象，其 {@code code} 字段必定为 {@code 0}
     * @throws BusinessException 参数校验失败、网络调用异常、响应为空或业务状态码异常时抛出
     */
    public PythonAgentResponse invoke(AgentType agentType, PythonAgentRequest request) {
        // 前置校验：agentType 不能为空
        if (agentType == null) {
            log.warn("[Python] call rejected: agentType is null");
            throw new BusinessException(ErrorCode.AGENT_TYPE_REQUIRED);
        }

        // 前置校验：Python 服务 base URL 必须已配置
        if (!StringUtils.hasText(baseUrl)) {
            log.error("[Python] call rejected: base URL is not configured agentType={}", agentType);
            throw new BusinessException(ErrorCode.PYTHON_BASE_URL_NOT_CONFIGURED);
        }

        String url = baseUrl + agentType.getPythonPath();
        String responseBody;
        long startTime = System.currentTimeMillis();

        // 构建 JSON 请求并通过 RestTemplate 发起 HTTP POST 调用
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

        // 校验 HTTP 响应体非空
        if (!StringUtils.hasText(responseBody)) {
            log.warn("[Python] call failed: empty response agentType={} url={}", agentType, url);
            throw new BusinessException(ErrorCode.PYTHON_EMPTY_RESPONSE);
        }

        // 解析 JSON 响应并校验业务状态码：code 不为 null 且为 0 时视为成功
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
