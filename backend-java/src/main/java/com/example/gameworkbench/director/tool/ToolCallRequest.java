package com.example.gameworkbench.director.tool;

import com.fasterxml.jackson.databind.JsonNode;

public record ToolCallRequest(String callId,String toolName,String toolVersion,String idempotencyKey,
        JsonNode arguments,boolean dryRun) {}
