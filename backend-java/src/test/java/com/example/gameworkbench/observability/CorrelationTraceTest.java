package com.example.gameworkbench.observability;

import com.example.gameworkbench.client.PythonAgentClient;
import com.example.gameworkbench.client.dto.PythonAgentRequest;
import com.example.gameworkbench.common.enums.AgentType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CorrelationTraceTest {
    @Test
    void acceptsOnlySafeInboundTraceAndClearsMdcAfterRequest() throws Exception {
        CorrelationIdFilter filter = new CorrelationIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        request.addHeader(CorrelationIdFilter.TRACE_HEADER, "trace-safe_1234");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                assertThat(MDC.get(DiagnosticContext.TRACE_ID)).isEqualTo("trace-safe_1234"));

        assertThat(response.getHeader(CorrelationIdFilter.TRACE_HEADER)).isEqualTo("trace-safe_1234");
        assertThat(MDC.get(DiagnosticContext.TRACE_ID)).isNull();
    }

    @Test
    void propagatesMdcTraceToPythonWithoutSendingCorrelationAsMetricLabel() {
        PythonAgentClient client = new PythonAgentClient(new ObjectMapper());
        ReflectionTestUtils.setField(client, "baseUrl", "http://python.test");
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("http://python.test/agent/requirement-breakdown"))
                .andExpect(header(CorrelationIdFilter.TRACE_HEADER, "trace-java-python"))
                .andRespond(withSuccess("{\"code\":0,\"message\":\"ok\",\"data\":{},\"trace_id\":\"trace-java-python\"}", MediaType.APPLICATION_JSON));

        MDC.put(DiagnosticContext.TRACE_ID, "trace-java-python");
        try {
            assertThat(client.invoke(AgentType.REQUIREMENT_BREAKDOWN,
                    PythonAgentRequest.builder().content("controlled fixture").build()).getTraceId())
                    .isEqualTo("trace-java-python");
        } finally {
            MDC.remove(DiagnosticContext.TRACE_ID);
        }
        server.verify();
    }
}
