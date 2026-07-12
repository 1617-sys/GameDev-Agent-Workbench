package com.example.gameworkbench.client;

import com.example.gameworkbench.client.dto.PythonAgentResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PythonAgentResponseContractTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesVersionedExecutionEnvelopeWithoutRawPromptOrOutput() throws Exception {
        PythonAgentResponse response = objectMapper.readValue("""
                {"code":0,"message":"success","trace_id":"trace-1","data":{
                  "status":"SUCCESS","output":{"content":"safe"},"raw_output_ref":null,
                  "model":"test-model","provider":"mock","usage":null,"latency_ms":12,"mock":true
                }}
                """, PythonAgentResponse.class);

        assertThat(response.getData().path("status").asText()).isEqualTo("SUCCESS");
        assertThat(response.getData().path("mock").asBoolean()).isTrue();
        assertThat(response.getData().has("prompt")).isFalse();
        assertThat(response.getData().has("raw_result")).isFalse();
        assertThat(response.getTraceId()).isEqualTo("trace-1");
    }
}
