package com.example.gameworkbench.director.tool;

import com.fasterxml.jackson.databind.JsonNode;

public record DirectorToolDefinition(String name,String version,JsonNode argumentSchema,ToolPermission permission,
        ToolRiskLevel riskLevel,long timeoutMs,int maxInlineResultBytes,boolean idempotent) {}
