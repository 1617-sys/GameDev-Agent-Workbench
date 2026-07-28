package com.example.gameworkbench.director.tool;

public record ToolCallResult(String callId,String toolName,String toolVersion,String status,String inputDigest,
        String outputDigest,String summary,String resultRef,long durationMs,String errorCode) {}
