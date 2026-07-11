package com.example.gameworkbench.messaging;

import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Component;

@Component
public class WorkflowErrorClassifier {
    public WorkflowErrorCode classify(Throwable error) {
        if (error instanceof IllegalArgumentException) return WorkflowErrorCode.INVALID_REQUEST;
        if (error instanceof SocketTimeoutException || error instanceof TimeoutException) return WorkflowErrorCode.NETWORK_TIMEOUT;
        String message = error.getMessage() == null ? "" : error.getMessage().toLowerCase();
        if (message.contains("429") || message.contains("rate limit")) return WorkflowErrorCode.PROVIDER_RATE_LIMIT;
        if (message.contains("prompt") || message.contains("schema") || message.contains("validation")) return WorkflowErrorCode.OUTPUT_VALIDATION;
        return WorkflowErrorCode.INFRASTRUCTURE;
    }
}
