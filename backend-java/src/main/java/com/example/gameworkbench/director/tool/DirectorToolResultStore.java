package com.example.gameworkbench.director.tool;

@FunctionalInterface
public interface DirectorToolResultStore {
    String put(long projectId,String runUuid,String callId,byte[] content);
}
