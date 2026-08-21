package com.example.gameworkbench.director.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;

@Component
@ConditionalOnProperty(name = "app.director.decision-provider", havingValue = "python")
public class HttpDirectorDecisionClient implements DirectorDecisionClient {
    private final RestTemplate http;private final String baseUrl;private final String token;
    public HttpDirectorDecisionClient(@Value("${app.python.base-url:http://127.0.0.1:8000}")String baseUrl,@Value("${app.python.internal-token}")String token,@Value("${app.director.decision-timeout-ms:30000}")int timeout){this.baseUrl=baseUrl;this.token=token;SimpleClientHttpRequestFactory factory=new SimpleClientHttpRequestFactory();factory.setConnectTimeout(Math.min(timeout,5000));factory.setReadTimeout(timeout);this.http=new RestTemplate(factory);}
    @Override public JsonNode decide(JsonNode snapshot,String traceId){try{HttpHeaders headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);headers.set("X-Internal-Token",token);headers.set("X-Trace-Id",traceId);JsonNode result=http.postForObject(baseUrl+"/director/decisions",new HttpEntity<>(snapshot,headers),JsonNode.class);if(result==null||!result.isObject())throw new BusinessException(ErrorCode.PYTHON_INVALID_RESPONSE);return result;}catch(BusinessException e){throw e;}catch(Exception e){throw new BusinessException(ErrorCode.PYTHON_CALL_FAILED);}}
}
